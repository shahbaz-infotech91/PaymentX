package com.paymentx.prompt.service;

import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.PaymentXException;
import com.paymentx.prompt.dto.CreatePromptRequest;
import com.paymentx.prompt.dto.CreatePromptVersionRequest;
import com.paymentx.prompt.dto.PromptResponse;
import com.paymentx.prompt.dto.PromptVariable;
import com.paymentx.prompt.dto.PromptVersionResponse;
import com.paymentx.prompt.dto.RenderPromptRequest;
import com.paymentx.prompt.dto.RenderPromptResponse;
import com.paymentx.prompt.entity.PromptStatus;
import com.paymentx.prompt.entity.PromptTemplate;
import com.paymentx.prompt.entity.PromptType;
import com.paymentx.prompt.entity.PromptVersion;
import com.paymentx.prompt.exception.PromptErrorCodes;
import com.paymentx.prompt.exception.PromptNotFoundException;
import com.paymentx.prompt.mapper.PromptVersionMapper;
import com.paymentx.prompt.metrics.PromptMetrics;
import com.paymentx.prompt.repository.PromptTemplateRepository;
import com.paymentx.prompt.repository.PromptVersionRepository;
import com.paymentx.prompt.service.impl.PromptServiceImpl;
import com.paymentx.prompt.util.PromptRenderer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * English:
 * Proves PromptServiceImpl's real business rules with mocked
 * repositories - matching RoutingServiceImplTest's plain-Mockito
 * convention (no Spring context, PromptRenderer/PromptVersionMapper
 * used as real, lightweight collaborators rather than mocked, since
 * they are pure/cheap and mocking them would just re-implement their
 * logic as stubs). What it verifies: duplicate prompt/version
 * rejection (both the fast-path existence check and the
 * DataIntegrityViolationException race backstop), not-found handling
 * for prompt/version/active-version, activation's atomic old-
 * deactivate + new-activate + concurrent-conflict-translation
 * behavior, deactivation's status guard, and render's delegation to
 * PromptRenderer with the real MISSING_VARIABLE/UNKNOWN_VARIABLE
 * outcomes surfacing as real exceptions.
 * Why it exists: Step 25 of the Phase 3.2 brief - the bulk of the
 * required test coverage (create/duplicate/version/activate/
 * deactivate/render/not-found).
 * How it communicates with other components: exercises
 * PromptServiceImpl directly; does not touch a real database or HTTP
 * layer (see PromptVersionRepositoryTest/PromptControllerIntegrationTest
 * for those).
 *
 * Hinglish:
 * PromptServiceImpl ke real business rules ko mocked repositories ke
 * saath prove karta hai - RoutingServiceImplTest ke plain-Mockito
 * convention se match karte hue (koi Spring context nahi,
 * PromptRenderer/PromptVersionMapper ko real, lightweight
 * collaborators ke roop me use kiya gaya hai, mock nahi, kyunki wo
 * pure/cheap hain aur unhe mock karna sirf unka logic stubs ke roop me
 * dobara implement karta. Ye kya verify karta hai: duplicate prompt/
 * version rejection (dono fast-path existence check aur
 * DataIntegrityViolationException race backstop), prompt/version/
 * active-version ke liye not-found handling, activation ka atomic
 * old-deactivate + new-activate + concurrent-conflict-translation
 * behavior, deactivation ka status guard, aur render ka PromptRenderer
 * ko delegation, real MISSING_VARIABLE/UNKNOWN_VARIABLE outcomes real
 * exceptions ke roop me surface hote hue.
 * Ye kyu hai: Phase 3.2 brief ka Step 25 - required test coverage ka
 * bulk (create/duplicate/version/activate/deactivate/render/not-found).
 * Dusre components se kaise communicate karta hai: PromptServiceImpl
 * ko directly exercise karta hai; koi real database ya HTTP layer
 * touch nahi karta (un ke liye
 * PromptVersionRepositoryTest/PromptControllerIntegrationTest dekho).
 */
@ExtendWith(MockitoExtension.class)
class PromptServiceImplTest {

    @Mock
    private PromptTemplateRepository promptTemplateRepository;
    @Mock
    private PromptVersionRepository promptVersionRepository;

    private PromptVersionMapper promptVersionMapper;
    private PromptRenderer promptRenderer;
    private PromptMetrics promptMetrics;
    private PromptServiceImpl promptService;

    private final UUID templateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        promptVersionMapper = new com.paymentx.prompt.mapper.PromptVersionMapperImpl();
        promptRenderer = new PromptRenderer();
        promptMetrics = new PromptMetrics(new SimpleMeterRegistry());
        promptService = new PromptServiceImpl(promptTemplateRepository, promptVersionRepository, promptVersionMapper, promptRenderer, promptMetrics);
    }

    private PromptTemplate template() {
        return PromptTemplate.builder().id(templateId).promptKey("PAYMENT_ERROR_ANALYSIS").name("Payment Error Analysis")
                .type(PromptType.SYSTEM).build();
    }

    private PromptVersion version(int number, PromptStatus status, List<PromptVariable> variables) {
        return PromptVersion.builder().id(UUID.randomUUID()).promptTemplateId(templateId).versionNumber(number)
                .content("Payment {{paymentReference}} failed because {{errorCode}}.").status(status).variables(variables).build();
    }

    private List<PromptVariable> variables() {
        return List.of(new PromptVariable("paymentReference", true, null), new PromptVariable("errorCode", true, null));
    }

    // ---- createPrompt ----

    @Test
    void createPrompt_newKey_createsTemplateAndDraftVersion1() {
        CreatePromptRequest request = new CreatePromptRequest("PAYMENT_ERROR_ANALYSIS", "Payment Error Analysis", null,
                PromptType.SYSTEM, "Payment {{paymentReference}} failed because {{errorCode}}.", variables());
        when(promptTemplateRepository.existsByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(false);
        when(promptTemplateRepository.saveAndFlush(any())).thenReturn(template());
        when(promptVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromptResponse response = promptService.createPrompt(request);

        assertThat(response.promptKey()).isEqualTo("PAYMENT_ERROR_ANALYSIS");
        assertThat(response.versionCount()).isEqualTo(1);
        assertThat(response.activeVersion()).isNull();
    }

    @Test
    void createPrompt_duplicateKey_throwsConflict() {
        CreatePromptRequest request = new CreatePromptRequest("PAYMENT_ERROR_ANALYSIS", "x", null, PromptType.SYSTEM, "hello", List.of());
        when(promptTemplateRepository.existsByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(true);

        assertThatThrownBy(() -> promptService.createPrompt(request))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.PROMPT_ALREADY_EXISTS);

        verify(promptTemplateRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPrompt_racedDuplicateKey_translatesConstraintViolationToConflict() {
        CreatePromptRequest request = new CreatePromptRequest("PAYMENT_ERROR_ANALYSIS", "x", null, PromptType.SYSTEM, "hello", List.of());
        when(promptTemplateRepository.existsByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(false);
        when(promptTemplateRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> promptService.createPrompt(request))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.PROMPT_ALREADY_EXISTS);
    }

    @Test
    void createPrompt_contentReferencesUndeclaredVariable_throwsInvalidContent() {
        CreatePromptRequest request = new CreatePromptRequest("X", "x", null, PromptType.SYSTEM,
                "Hello {{name}}, ref {{paymentReference}}", List.of(new PromptVariable("paymentReference", true, null)));

        assertThatThrownBy(() -> promptService.createPrompt(request))
                .isInstanceOf(PaymentXException.class)
                .extracting(ex -> ((PaymentXException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.INVALID_PROMPT_CONTENT);

        verify(promptTemplateRepository, never()).existsByPromptKey(any());
    }

    // ---- getPrompt / not found ----

    @Test
    void getPrompt_unknownKey_throwsPromptNotFound() {
        when(promptTemplateRepository.findByPromptKey("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promptService.getPrompt("MISSING"))
                .isInstanceOf(PromptNotFoundException.class)
                .extracting(ex -> ((PromptNotFoundException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.PROMPT_NOT_FOUND);
    }

    @Test
    void getActiveVersion_noActiveVersion_throwsNoActiveVersion() {
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndStatus(templateId, PromptStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promptService.getActiveVersion("PAYMENT_ERROR_ANALYSIS"))
                .isInstanceOf(PromptNotFoundException.class)
                .extracting(ex -> ((PromptNotFoundException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.NO_ACTIVE_VERSION);
    }

    @Test
    void getVersion_unknownVersionNumber_throwsVersionNotFound() {
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndVersionNumber(templateId, 5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promptService.getVersion("PAYMENT_ERROR_ANALYSIS", 5))
                .isInstanceOf(PromptNotFoundException.class)
                .extracting(ex -> ((PromptNotFoundException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.PROMPT_VERSION_NOT_FOUND);
    }

    // ---- createVersion ----

    @Test
    void createVersion_computesNextVersionNumberFromMax() {
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findMaxVersionNumber(templateId)).thenReturn(3);
        when(promptVersionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreatePromptVersionRequest request = new CreatePromptVersionRequest("Payment {{paymentReference}} failed because {{errorCode}}.", variables());
        PromptVersionResponse response = promptService.createVersion("PAYMENT_ERROR_ANALYSIS", request);

        assertThat(response.versionNumber()).isEqualTo(4);
        assertThat(response.status()).isEqualTo(PromptStatus.DRAFT);
    }

    @Test
    void createVersion_racedDuplicateVersionNumber_translatesConstraintViolationToConflict() {
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findMaxVersionNumber(templateId)).thenReturn(1);
        when(promptVersionRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique violation"));

        CreatePromptVersionRequest request = new CreatePromptVersionRequest("Payment {{paymentReference}} failed because {{errorCode}}.", variables());

        assertThatThrownBy(() -> promptService.createVersion("PAYMENT_ERROR_ANALYSIS", request))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.PROMPT_VERSION_ALREADY_EXISTS);
    }

    // ---- activateVersion ----

    @Test
    void activateVersion_deactivatesPreviousActiveAndActivatesTarget() {
        PromptVersion currentActive = version(1, PromptStatus.ACTIVE, variables());
        PromptVersion target = version(2, PromptStatus.DRAFT, variables());
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndVersionNumber(templateId, 2)).thenReturn(Optional.of(target));
        when(promptVersionRepository.findByPromptTemplateIdAndStatus(templateId, PromptStatus.ACTIVE)).thenReturn(Optional.of(currentActive));
        when(promptVersionRepository.save(currentActive)).thenReturn(currentActive);
        when(promptVersionRepository.saveAndFlush(target)).thenReturn(target);

        promptService.activateVersion("PAYMENT_ERROR_ANALYSIS", 2);

        assertThat(currentActive.getStatus()).isEqualTo(PromptStatus.INACTIVE);
        assertThat(target.getStatus()).isEqualTo(PromptStatus.ACTIVE);
        verify(promptVersionRepository, times(1)).save(currentActive);
        verify(promptVersionRepository, times(1)).saveAndFlush(target);
    }

    @Test
    void activateVersion_archivedVersion_throwsInvalidStatus() {
        PromptVersion archived = version(1, PromptStatus.ARCHIVED, variables());
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndVersionNumber(templateId, 1)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> promptService.activateVersion("PAYMENT_ERROR_ANALYSIS", 1))
                .isInstanceOf(PaymentXException.class)
                .extracting(ex -> ((PaymentXException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.INVALID_PROMPT_STATUS);
    }

    @Test
    void activateVersion_alreadyActive_isIdempotentNoOp() {
        PromptVersion alreadyActive = version(1, PromptStatus.ACTIVE, variables());
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndVersionNumber(templateId, 1)).thenReturn(Optional.of(alreadyActive));

        promptService.activateVersion("PAYMENT_ERROR_ANALYSIS", 1);

        verify(promptVersionRepository, never()).saveAndFlush(any());
    }

    @Test
    void activateVersion_concurrentActivationRace_translatesConstraintViolationToActiveVersionConflict() {
        PromptVersion target = version(2, PromptStatus.DRAFT, variables());
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndVersionNumber(templateId, 2)).thenReturn(Optional.of(target));
        when(promptVersionRepository.findByPromptTemplateIdAndStatus(templateId, PromptStatus.ACTIVE)).thenReturn(Optional.empty());
        when(promptVersionRepository.saveAndFlush(target)).thenThrow(new DataIntegrityViolationException("partial unique index violation"));

        assertThatThrownBy(() -> promptService.activateVersion("PAYMENT_ERROR_ANALYSIS", 2))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.ACTIVE_VERSION_CONFLICT);
    }

    // ---- deactivateVersion ----

    @Test
    void deactivateVersion_notCurrentlyActive_throwsInvalidStatus() {
        PromptVersion draft = version(1, PromptStatus.DRAFT, variables());
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndVersionNumber(templateId, 1)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> promptService.deactivateVersion("PAYMENT_ERROR_ANALYSIS", 1))
                .isInstanceOf(PaymentXException.class)
                .extracting(ex -> ((PaymentXException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.INVALID_PROMPT_STATUS);
    }

    @Test
    void deactivateVersion_active_setsInactive() {
        PromptVersion active = version(1, PromptStatus.ACTIVE, variables());
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndVersionNumber(templateId, 1)).thenReturn(Optional.of(active));
        when(promptVersionRepository.save(active)).thenReturn(active);

        PromptVersionResponse response = promptService.deactivateVersion("PAYMENT_ERROR_ANALYSIS", 1);

        assertThat(response.status()).isEqualTo(PromptStatus.INACTIVE);
    }

    // ---- renderPrompt ----

    @Test
    void renderPrompt_usesActiveVersionWhenNoExplicitVersionRequested() {
        PromptVersion active = version(1, PromptStatus.ACTIVE, variables());
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndStatus(templateId, PromptStatus.ACTIVE)).thenReturn(Optional.of(active));

        RenderPromptResponse response = promptService.renderPrompt("PAYMENT_ERROR_ANALYSIS",
                new RenderPromptRequest(null, java.util.Map.of("paymentReference", "PMT-123", "errorCode", "PMT-409")));

        assertThat(response.renderedContent()).isEqualTo("Payment PMT-123 failed because PMT-409.");
        assertThat(response.version()).isEqualTo(1);
    }

    @Test
    void renderPrompt_missingRequiredVariable_throwsMissingVariable() {
        PromptVersion active = version(1, PromptStatus.ACTIVE, variables());
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndStatus(templateId, PromptStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> promptService.renderPrompt("PAYMENT_ERROR_ANALYSIS",
                new RenderPromptRequest(null, java.util.Map.of("paymentReference", "PMT-123"))))
                .isInstanceOf(PaymentXException.class)
                .extracting(ex -> ((PaymentXException) ex).getErrorCode())
                .isEqualTo(PromptErrorCodes.MISSING_VARIABLE);
    }

    @Test
    void renderPrompt_explicitVersionRequested_bypassesActiveLookup() {
        PromptVersion archived = version(3, PromptStatus.ARCHIVED, variables());
        when(promptTemplateRepository.findByPromptKey("PAYMENT_ERROR_ANALYSIS")).thenReturn(Optional.of(template()));
        when(promptVersionRepository.findByPromptTemplateIdAndVersionNumber(templateId, 3)).thenReturn(Optional.of(archived));

        RenderPromptResponse response = promptService.renderPrompt("PAYMENT_ERROR_ANALYSIS",
                new RenderPromptRequest(3, java.util.Map.of("paymentReference", "PMT-1", "errorCode", "E1")));

        assertThat(response.version()).isEqualTo(3);
        verify(promptVersionRepository, never()).findByPromptTemplateIdAndStatus(eq(templateId), eq(PromptStatus.ACTIVE));
    }
}
