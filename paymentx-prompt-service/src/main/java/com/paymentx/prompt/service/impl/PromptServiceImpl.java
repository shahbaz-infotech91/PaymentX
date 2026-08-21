package com.paymentx.prompt.service.impl;

import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.PaymentXException;
import com.paymentx.prompt.dto.CreatePromptRequest;
import com.paymentx.prompt.dto.CreatePromptVersionRequest;
import com.paymentx.prompt.dto.PromptResponse;
import com.paymentx.prompt.dto.PromptVersionResponse;
import com.paymentx.prompt.dto.RenderPromptRequest;
import com.paymentx.prompt.dto.RenderPromptResponse;
import com.paymentx.prompt.entity.PromptStatus;
import com.paymentx.prompt.entity.PromptTemplate;
import com.paymentx.prompt.entity.PromptVersion;
import com.paymentx.prompt.exception.PromptErrorCodes;
import com.paymentx.prompt.exception.PromptNotFoundException;
import com.paymentx.prompt.mapper.PromptVersionMapper;
import com.paymentx.prompt.metrics.PromptMetrics;
import com.paymentx.prompt.repository.PromptTemplateRepository;
import com.paymentx.prompt.repository.PromptVersionRepository;
import com.paymentx.prompt.service.PromptService;
import com.paymentx.prompt.util.PromptRenderer;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * English:
 * The real implementation of prompt management - create/version/
 * activate/deactivate/render, with every write wrapped in a real
 * transaction (class-level @Transactional, matching
 * RoutingServiceImpl's convention) and every mutation-worthy state
 * change guarded by BOTH an application-level pre-check (for a fast,
 * readable error on the common case) AND the database's own
 * constraints (the real backstop for concurrent requests - Step 20 of
 * the Phase 3.2 brief: "do not rely only on Java in-memory
 * synchronization").
 *
 * <p>WHY activateVersion() calls promptVersionRepository.flush()
 * before returning from its try block: without it, Hibernate would
 * batch the deactivate-old/activate-new UPDATEs and defer sending them
 * to Postgres until the transaction commits (or some later, unrelated
 * flush) - the partial unique index violation from two concurrent
 * activate calls would then surface either at commit time (outside
 * this method's try/catch, as a generic transaction-rollback failure
 * with no chance to translate it to a real ACTIVE_VERSION_CONFLICT
 * response) or not at the expected call site at all. flush() forces
 * Postgres to evaluate the constraint immediately, inside this method,
 * so the catch block can turn a real
 * DataIntegrityViolationException into the documented 409 response.
 *
 * <p>WHY a freshly created version never starts ACTIVE: Step 4 of the
 * brief requires an explicit activation step - auto-activating on
 * create would mean a caller could never stage a new version for
 * review without it silently taking over live traffic.
 *
 * Why it exists: PromptService's implementation - Step 8/20/21 of the
 * Phase 3.2 brief.
 * How it communicates with other components: injected into
 * PromptController; calls PromptTemplateRepository/
 * PromptVersionRepository (Postgres), PromptRenderer (pure in-process
 * logic, no I/O), PromptMetrics (Micrometer/Prometheus) - never calls
 * any other PaymentX service or an LLM.
 *
 * Hinglish:
 * Prompt management ka real implementation - create/version/activate/
 * deactivate/render, har write ek real transaction me wrapped (class-
 * level @Transactional, RoutingServiceImpl ke convention se match karte
 * hue) aur har mutation-worthy state change DONO se guarded - ek
 * application-level pre-check (common case ke liye ek fast, readable
 * error ke liye) AUR database ke apne constraints (concurrent requests
 * ke liye real backstop - Phase 3.2 brief ka Step 20: "sirf Java
 * in-memory synchronization par rely mat karo").
 *
 * <p>activateVersion() apne try block se return karne se pehle
 * promptVersionRepository.flush() KYU call karta hai: iske bina,
 * Hibernate deactivate-old/activate-new UPDATEs ko batch kar deta aur
 * Postgres ko bhejna transaction commit hone tak (ya kisi baad wali,
 * unrelated flush tak) defer kar deta - do concurrent activate calls
 * se partial unique index violation phir ya toh commit time par surface
 * hoti (is method ke try/catch se bahar, ek generic transaction-
 * rollback failure ke roop me, jise ek real ACTIVE_VERSION_CONFLICT
 * response me translate karne ka koi mauka nahi) ya expected call site
 * par bilkul nahi hoti. flush() Postgres ko constraint ko turant
 * evaluate karne par majboor karta hai, isi method ke andar, taaki catch
 * block ek real DataIntegrityViolationException ko documented 409
 * response me badal sake.
 *
 * <p>Ek freshly created version kabhi ACTIVE se start KYU nahi hoti:
 * brief ka Step 4 ek explicit activation step maangta hai - create par
 * auto-activate karne ka matlab hota ki ek caller kabhi ek naye version
 * ko review ke liye stage hi nahi kar sakta bina use silently live
 * traffic le lene diye.
 *
 * Ye kyu hai: PromptService ka implementation - Phase 3.2 brief ka Step
 * 8/20/21.
 * Dusre components se kaise communicate karta hai: PromptController me
 * inject hota hai; PromptTemplateRepository/PromptVersionRepository
 * (Postgres), PromptRenderer (pure in-process logic, koi I/O nahi),
 * PromptMetrics (Micrometer/Prometheus) ko call karta hai - kabhi kisi
 * doosri PaymentX service ya ek LLM ko call nahi karta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PromptServiceImpl implements PromptService {

    private final PromptTemplateRepository promptTemplateRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final PromptVersionMapper promptVersionMapper;
    private final PromptRenderer promptRenderer;
    private final PromptMetrics promptMetrics;

    @Override
    public PromptResponse createPrompt(CreatePromptRequest request) {
        promptMetrics.recordRequest("createPrompt");
        promptRenderer.validateContentDeclaresOnlyKnownVariables(request.content(), request.variables());

        if (promptTemplateRepository.existsByPromptKey(request.key())) {
            throw new ConflictException(PromptErrorCodes.PROMPT_ALREADY_EXISTS, "A prompt already exists with key: " + request.key());
        }

        PromptTemplate template = PromptTemplate.builder()
                .promptKey(request.key())
                .name(request.name())
                .description(request.description())
                .type(request.type())
                .build();
        try {
            template = promptTemplateRepository.saveAndFlush(template);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(PromptErrorCodes.PROMPT_ALREADY_EXISTS, "A prompt already exists with key: " + request.key());
        }

        PromptVersion version = PromptVersion.builder()
                .promptTemplateId(template.getId())
                .versionNumber(1)
                .content(request.content())
                .status(PromptStatus.DRAFT)
                .variables(request.variables())
                .build();
        version = promptVersionRepository.save(version);

        log.info("Prompt created promptKey={} version=1 status=DRAFT", request.key());
        return toPromptResponse(template, null, 1);
    }

    @Override
    @Transactional(readOnly = true)
    public PromptResponse getPrompt(String promptKey) {
        promptMetrics.recordRequest("getPrompt");
        PromptTemplate template = findTemplateOrThrow(promptKey);
        PromptVersion active = promptVersionRepository
                .findByPromptTemplateIdAndStatus(template.getId(), PromptStatus.ACTIVE)
                .orElse(null);
        long versionCount = promptVersionRepository.countByPromptTemplateId(template.getId());
        return toPromptResponse(template, active, (int) versionCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptVersionResponse> listVersions(String promptKey) {
        promptMetrics.recordRequest("listVersions");
        PromptTemplate template = findTemplateOrThrow(promptKey);
        return promptVersionRepository.findByPromptTemplateIdOrderByVersionNumberDesc(template.getId()).stream()
                .map(version -> promptVersionMapper.toResponse(version, promptKey))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromptVersionResponse getVersion(String promptKey, int versionNumber) {
        promptMetrics.recordRequest("getVersion");
        PromptTemplate template = findTemplateOrThrow(promptKey);
        return promptVersionMapper.toResponse(findVersionOrThrow(template, versionNumber), promptKey);
    }

    @Override
    @Transactional(readOnly = true)
    public PromptVersionResponse getActiveVersion(String promptKey) {
        promptMetrics.recordRequest("getActiveVersion");
        Timer.Sample timer = promptMetrics.startActiveLookupTimer();
        try {
            PromptTemplate template = findTemplateOrThrow(promptKey);
            PromptVersion active = promptVersionRepository
                    .findByPromptTemplateIdAndStatus(template.getId(), PromptStatus.ACTIVE)
                    .orElseThrow(() -> new PromptNotFoundException(PromptErrorCodes.NO_ACTIVE_VERSION,
                            "No active version for prompt: " + promptKey));
            return promptVersionMapper.toResponse(active, promptKey);
        } finally {
            promptMetrics.stopActiveLookupTimer(timer, promptKey);
        }
    }

    @Override
    public PromptVersionResponse createVersion(String promptKey, CreatePromptVersionRequest request) {
        promptMetrics.recordRequest("createVersion");
        promptRenderer.validateContentDeclaresOnlyKnownVariables(request.content(), request.variables());

        PromptTemplate template = findTemplateOrThrow(promptKey);
        int nextVersion = promptVersionRepository.findMaxVersionNumber(template.getId()) + 1;

        PromptVersion version = PromptVersion.builder()
                .promptTemplateId(template.getId())
                .versionNumber(nextVersion)
                .content(request.content())
                .status(PromptStatus.DRAFT)
                .variables(request.variables())
                .build();
        try {
            version = promptVersionRepository.saveAndFlush(version);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(PromptErrorCodes.PROMPT_VERSION_ALREADY_EXISTS,
                    "Version " + nextVersion + " already exists for prompt: " + promptKey);
        }

        log.info("Prompt version created promptKey={} version={} status=DRAFT", promptKey, nextVersion);
        return promptVersionMapper.toResponse(version, promptKey);
    }

    @Override
    public PromptVersionResponse activateVersion(String promptKey, int versionNumber) {
        promptMetrics.recordRequest("activateVersion");
        PromptTemplate template = findTemplateOrThrow(promptKey);
        PromptVersion target = findVersionOrThrow(template, versionNumber);

        if (target.getStatus() == PromptStatus.ARCHIVED) {
            throw new PaymentXException(PromptErrorCodes.INVALID_PROMPT_STATUS,
                    "Version " + versionNumber + " of prompt " + promptKey + " is ARCHIVED and cannot be activated", false);
        }
        if (target.getStatus() == PromptStatus.ACTIVE) {
            return promptVersionMapper.toResponse(target, promptKey);
        }

        promptVersionRepository.findByPromptTemplateIdAndStatus(template.getId(), PromptStatus.ACTIVE)
                .ifPresent(currentActive -> {
                    currentActive.setStatus(PromptStatus.INACTIVE);
                    promptVersionRepository.save(currentActive);
                });

        target.setStatus(PromptStatus.ACTIVE);
        try {
            target = promptVersionRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(PromptErrorCodes.ACTIVE_VERSION_CONFLICT,
                    "Another version of prompt " + promptKey + " was activated concurrently - retry");
        }

        log.info("Prompt version activated promptKey={} version={}", promptKey, versionNumber);
        return promptVersionMapper.toResponse(target, promptKey);
    }

    @Override
    public PromptVersionResponse deactivateVersion(String promptKey, int versionNumber) {
        promptMetrics.recordRequest("deactivateVersion");
        PromptTemplate template = findTemplateOrThrow(promptKey);
        PromptVersion target = findVersionOrThrow(template, versionNumber);

        if (target.getStatus() != PromptStatus.ACTIVE) {
            throw new PaymentXException(PromptErrorCodes.INVALID_PROMPT_STATUS,
                    "Version " + versionNumber + " of prompt " + promptKey + " is not ACTIVE (current status: " + target.getStatus() + ")", false);
        }

        target.setStatus(PromptStatus.INACTIVE);
        target = promptVersionRepository.save(target);
        log.info("Prompt version deactivated promptKey={} version={}", promptKey, versionNumber);
        return promptVersionMapper.toResponse(target, promptKey);
    }

    @Override
    @Transactional(readOnly = true)
    public RenderPromptResponse renderPrompt(String promptKey, RenderPromptRequest request) {
        promptMetrics.recordRequest("render");
        Timer.Sample timer = promptMetrics.startRenderTimer();
        try {
            PromptTemplate template = findTemplateOrThrow(promptKey);
            PromptVersion version = request.version() != null
                    ? findVersionOrThrow(template, request.version())
                    : promptVersionRepository.findByPromptTemplateIdAndStatus(template.getId(), PromptStatus.ACTIVE)
                        .orElseThrow(() -> new PromptNotFoundException(PromptErrorCodes.NO_ACTIVE_VERSION,
                                "No active version for prompt: " + promptKey));

            PromptRenderer.RenderResult result;
            try {
                result = promptRenderer.render(version.getContent(), version.getVariables(), request.variables());
            } catch (PaymentXException ex) {
                promptMetrics.recordRenderFailure(promptKey, ex.getErrorCode());
                promptMetrics.recordValidationFailure(ex.getErrorCode());
                throw ex;
            }

            promptMetrics.recordRenderSuccess(promptKey);
            // Step 14: never log the full rendered content by default - metadata only.
            log.info("Prompt rendered promptKey={} version={} variablesUsedCount={}",
                    promptKey, version.getVersionNumber(), result.variablesUsed().size());
            return new RenderPromptResponse(promptKey, version.getVersionNumber(), result.renderedContent(), result.variablesUsed());
        } finally {
            promptMetrics.stopRenderTimer(timer, promptKey);
        }
    }

    private PromptResponse toPromptResponse(PromptTemplate template, PromptVersion activeVersionOrNull, int versionCount) {
        PromptVersionResponse activeResponse = activeVersionOrNull == null
                ? null
                : promptVersionMapper.toResponse(activeVersionOrNull, template.getPromptKey());
        return new PromptResponse(
                template.getPromptKey(), template.getName(), template.getDescription(), template.getType(),
                versionCount, activeResponse,
                template.getCreatedAt(), template.getCreatedBy(), template.getUpdatedAt(), template.getUpdatedBy());
    }

    private PromptTemplate findTemplateOrThrow(String promptKey) {
        return promptTemplateRepository.findByPromptKey(promptKey)
                .orElseThrow(() -> new PromptNotFoundException(PromptErrorCodes.PROMPT_NOT_FOUND, "No prompt found with key: " + promptKey));
    }

    private PromptVersion findVersionOrThrow(PromptTemplate template, int versionNumber) {
        return promptVersionRepository.findByPromptTemplateIdAndVersionNumber(template.getId(), versionNumber)
                .orElseThrow(() -> new PromptNotFoundException(PromptErrorCodes.PROMPT_VERSION_NOT_FOUND,
                        "No version " + versionNumber + " found for prompt: " + template.getPromptKey()));
    }
}
