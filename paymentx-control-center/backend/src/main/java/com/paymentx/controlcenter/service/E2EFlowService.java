package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.config.ControlCenterProperties;
import com.paymentx.controlcenter.dto.e2e.E2ERunResult;
import com.paymentx.controlcenter.dto.e2e.E2EStage;
import com.paymentx.controlcenter.dto.e2e.E2EStageStatus;
import com.paymentx.controlcenter.dto.postgres.PaymentFlowDetail;
import com.paymentx.controlcenter.dto.postgres.PaymentFlowStage;
import com.paymentx.controlcenter.dto.postgres.PaymentSummary;
import com.paymentx.controlcenter.dto.postgres.ParticipantSummary;
import com.paymentx.controlcenter.exception.ControlCenterException;
import com.paymentx.controlcenter.repository.ParticipantRepository;
import com.paymentx.controlcenter.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ENGLISH: Orchestrates one real, bounded, end-to-end payment through
 * the REAL platform - the same real "generate a test API key, POST to
 * the real Gateway, poll for the real payment row" mechanism
 * paymentx-validation-suite/scripts/run-e2e.ps1's Step 7 already uses,
 * never a second, hand-rolled implementation of the payment engine
 * itself. What it does per stage: Gateway/Authentication are set from
 * this backend's own real HTTP call outcome (it made the call, so it
 * genuinely knows); Validation comes from the real ValidationResponse
 * body; Routing/Payment/Audit/Notification/Reconciliation/Reporting
 * are all delegated straight to the existing, already-tested
 * PaymentFlowService once a real payment row appears - this class
 * never independently decides whether routing succeeded or an audit
 * event was recorded, it only asks the service that already knows how
 * to answer that from real data. Why it exists: the Phase 5 "use the
 * EXISTING PaymentX E2E mechanism where possible, do NOT duplicate the
 * payment business logic inside the dashboard" requirement, satisfied
 * literally - the actual debit/credit/settle/routing/audit/
 * notification logic lives in the real services, exactly as it always
 * has; this class only calls them and reads their real results.
 *
 * HINGLISH: Real platform ke through ek real, bounded, end-to-end
 * payment orchestrate karta hai - wahi real "ek test API key generate
 * karo, real Gateway ko POST karo, real payment row ke liye poll karo"
 * mechanism jo paymentx-validation-suite/scripts/run-e2e.ps1 ka Step 7
 * already use karta hai, kabhi payment engine ka khud ek doosra,
 * hand-rolled implementation nahi. Ye har stage ke liye kya karti hai:
 * Gateway/Authentication is backend ke apne real HTTP call outcome se
 * set hote hain (isne khud call ki, isliye ise genuinely pata hai);
 * Validation real ValidationResponse body se aata hai;
 * Routing/Payment/Audit/Notification/Reconciliation/Reporting sab
 * seedhe existing, already-tested PaymentFlowService ko delegate hote
 * hain jab ek real payment row aa jaaye - ye class kabhi independently
 * decide nahi karti ki routing succeed hui ya ek audit event record
 * hua, ye sirf us service se poochti hai jise pehle se pata hai ki
 * real data se ye kaise jawab dena hai. Ye dashboard me kyu hai: Phase
 * 5 ka "EXISTING PaymentX E2E mechanism jahan possible ho use karo,
 * dashboard ke andar payment business logic duplicate MAT karo"
 * requirement, literally satisfy kiya gaya - actual
 * debit/credit/settle/routing/audit/notification logic real services
 * me hi rehti hai, bilkul jaise hamesha se rahi hai; ye class sirf
 * unhe call karti hai aur unke real results padhti hai.
 */
@Service
public class E2EFlowService {

    private static final Logger log = LoggerFactory.getLogger(E2EFlowService.class);

    private static final Set<String> SUCCESSFUL_PAYMENT_STATUSES = Set.of("SETTLED");
    private static final Set<String> FAILED_PAYMENT_STATUSES = Set.of(
            "DEBIT_FAILED", "CREDIT_FAILED", "FAILED", "TIMEOUT", "CANCELLED", "RETURNED", "REVERSED");
    private static final String REDIS_API_KEY_PREFIX = "gateway:apikey:";
    private static final List<String> STAGE_NAMES = List.of(
            "Gateway", "Authentication", "Validation", "Routing", "Payment", "Audit", "Notification", "Reconciliation", "Reporting");

    private final ControlCenterProperties properties;
    private final RestTemplate restTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ParticipantRepository participantRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentFlowService paymentFlowService;

    private final Object lock = new Object();
    private final LinkedHashMap<String, E2ERunResult> runsById = new LinkedHashMap<>();
    private final ExecutorService executor;

    public E2EFlowService(ControlCenterProperties properties,
                           @Qualifier("paymentXServiceRestTemplate") RestTemplate restTemplate,
                           RedisConnectionFactory redisConnectionFactory,
                           ParticipantRepository participantRepository,
                           PaymentRepository paymentRepository,
                           PaymentFlowService paymentFlowService) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.participantRepository = participantRepository;
        this.paymentRepository = paymentRepository;
        this.paymentFlowService = paymentFlowService;
        AtomicInteger threadCount = new AtomicInteger(0);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "e2e-flow-runner-" + threadCount.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(2, threadFactory);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public E2ERunResult startRun() {
        String runId = UUID.randomUUID().toString();
        String reference = "CC-E2E-" + System.currentTimeMillis() + "-" + runId.substring(0, 8).toUpperCase();
        String correlationId = UUID.randomUUID().toString();
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);

        List<E2EStage> initialStages = new ArrayList<>();
        initialStages.add(new E2EStage("Gateway", E2EStageStatus.RUNNING, "Sending POST /api/v1/validations to the real API Gateway.", null));
        for (String name : STAGE_NAMES.subList(1, STAGE_NAMES.size())) {
            initialStages.add(new E2EStage(name, E2EStageStatus.PENDING, "Not reached yet.", null));
        }
        E2ERunResult initial = new E2ERunResult(runId, reference, correlationId, null,
                E2EStageStatus.RUNNING, startedAt, null, 0, null, initialStages);
        putRun(initial);

        executor.submit(() -> runFlow(runId, reference, correlationId, startedAt));
        return initial;
    }

    public Optional<E2ERunResult> getRun(String runId) {
        synchronized (lock) {
            return Optional.ofNullable(runsById.get(runId));
        }
    }

    public List<E2ERunResult> history() {
        synchronized (lock) {
            List<E2ERunResult> list = new ArrayList<>(runsById.values());
            java.util.Collections.reverse(list);
            return list;
        }
    }

    private void putRun(E2ERunResult result) {
        synchronized (lock) {
            runsById.put(result.runId(), result);
            int limit = properties.getE2e().getHistoryLimit();
            while (runsById.size() > limit) {
                var it = runsById.keySet().iterator();
                it.next();
                it.remove();
            }
        }
    }

    private void runFlow(String runId, String reference, String correlationId, OffsetDateTime startedAt) {
        long deadlineEpochMillis = startedAt.toInstant().toEpochMilli() + properties.getE2e().getMaxDurationSeconds() * 1000L;

        List<ParticipantSummary> active = participantRepository.findActive(5);
        if (active.size() < 2) {
            finish(runId, reference, correlationId, null, startedAt, E2EStageStatus.FAILED,
                    "Need at least 2 ACTIVE participants in paymentx_validation.participant to run a real E2E payment; found " + active.size() + ".",
                    List.of(stageFailed("Gateway", "No real request was sent - not enough real ACTIVE participants exist.")));
            return;
        }
        String debtorBankId = active.get(0).bankId();
        String creditorBankId = active.get(1).bankId();

        String apiKey = "CC-E2E-" + UUID.randomUUID().toString().replace("-", "");
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            byte[] redisKey = (REDIS_API_KEY_PREFIX + apiKey).getBytes(StandardCharsets.UTF_8);
            connection.stringCommands().set(redisKey, debtorBankId.getBytes(StandardCharsets.UTF_8));
            connection.keyCommands().expire(redisKey, properties.getE2e().getApiKeyTtlSeconds());
        } catch (Exception redisFailure) {
            log.warn("E2E run {} could not seed a real test API key into Redis: {}", runId, redisFailure.getMessage());
            finish(runId, reference, correlationId, null, startedAt, E2EStageStatus.FAILED,
                    "Could not seed a real, ephemeral test API key into Redis: " + redisFailure.getMessage(),
                    List.of(stageFailed("Gateway", "No real request was sent - Redis (the real credential store ApiKeyAuthenticationGlobalFilter reads) was unreachable.")));
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentReference", reference);
        body.put("scheme", "INSTANT_PAYMENT");
        body.put("amount", new BigDecimal("1.00"));
        body.put("currency", "USD");
        body.put("debtorAccount", "CC-E2E-DEBTOR");
        body.put("debtorBankId", debtorBankId);
        body.put("creditorAccount", "CC-E2E-CREDITOR");
        body.put("creditorBankId", creditorBankId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", apiKey);
        headers.set("X-Correlation-Id", correlationId);

        String url = properties.getServices().getApiGatewayUrl() + "/api/v1/validations";
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        } catch (RestClientResponseException httpError) {
            int code = httpError.getStatusCode().value();
            List<E2EStage> stages = new ArrayList<>();
            stages.add(stageOk("Gateway", "Reached the real Gateway (HTTP " + code + ")."));
            if (code == 401 || code == 403) {
                stages.add(stageFailed("Authentication", "The real Gateway rejected the real test API key (HTTP " + code + ")."));
                finish(runId, reference, correlationId, null, startedAt, E2EStageStatus.FAILED,
                        "Authentication failed at the real Gateway (HTTP " + code + ").", stages);
            } else {
                stages.add(stageOk("Authentication", "Real test API key accepted."));
                stages.add(stageFailed("Validation", "Real Validation-Service returned HTTP " + code + "."));
                finish(runId, reference, correlationId, null, startedAt, E2EStageStatus.FAILED,
                        "Validation request failed with HTTP " + code + ".", stages);
            }
            return;
        } catch (ResourceAccessException connectionFailure) {
            finish(runId, reference, correlationId, null, startedAt, E2EStageStatus.FAILED,
                    "Could not reach the real API Gateway: " + connectionFailure.getMostSpecificCause().getMessage(),
                    List.of(stageFailed("Gateway", "Connection to the real API Gateway failed.")));
            return;
        }

        Map<?, ?> responseBody = response.getBody();
        String traceId = responseBody != null && responseBody.get("traceId") != null ? String.valueOf(responseBody.get("traceId")) : null;
        String validationStatus = responseBody != null ? String.valueOf(responseBody.get("status")) : null;

        List<E2EStage> earlyStages = new ArrayList<>();
        earlyStages.add(stageOk("Gateway", "Reached the real Gateway (HTTP " + response.getStatusCode().value() + ")."));
        earlyStages.add(stageOk("Authentication", "Real test API key accepted."));

        if (!"VALIDATED".equals(validationStatus)) {
            String reason = responseBody != null && responseBody.get("rejectionReason") != null
                    ? String.valueOf(responseBody.get("rejectionReason")) : "status=" + validationStatus;
            earlyStages.add(stageFailed("Validation", "Real Validation-Service returned status=" + validationStatus + " (" + reason + ")."));
            finish(runId, reference, correlationId, traceId, startedAt, E2EStageStatus.FAILED,
                    "Validation rejected the payment: " + reason, earlyStages);
            return;
        }
        earlyStages.add(stageOk("Validation", "Real Validation-Service returned VALIDATED."));
        putRun(snapshot(runId, reference, correlationId, traceId, E2EStageStatus.RUNNING, startedAt, null, fillPending(earlyStages)));

        // --- Async downstream: validation-service -> Kafka -> payment-service consumer runs the real engine synchronously. ---
        while (System.currentTimeMillis() < deadlineEpochMillis) {
            Optional<PaymentSummary> paymentOpt = paymentRepository.findByReference(reference);
            if (paymentOpt.isPresent()) {
                PaymentSummary payment = paymentOpt.get();
                PaymentFlowDetail flow = paymentFlowService.flowForReference(reference);
                List<E2EStage> merged = mergeStages(earlyStages, flow.stages());
                boolean terminal = SUCCESSFUL_PAYMENT_STATUSES.contains(payment.status()) || FAILED_PAYMENT_STATUSES.contains(payment.status());
                if (terminal) {
                    boolean success = SUCCESSFUL_PAYMENT_STATUSES.contains(payment.status());
                    String failureReason = success ? null
                            : (payment.failureReason() != null ? payment.failureReason() : "Payment reached terminal status " + payment.status());
                    finish(runId, reference, correlationId, traceId, startedAt,
                            success ? E2EStageStatus.SUCCESS : E2EStageStatus.FAILED, failureReason, merged);
                    return;
                }
                putRun(snapshot(runId, reference, correlationId, traceId, E2EStageStatus.RUNNING, startedAt, null, merged));
            }
            sleep(properties.getE2e().getPollIntervalMillis());
        }

        // --- Bounded deadline reached without a terminal payment status - report TIMEOUT honestly, never SUCCESS. ---
        Optional<PaymentSummary> lastKnown = paymentRepository.findByReference(reference);
        List<E2EStage> diagnosticStages;
        if (lastKnown.isPresent()) {
            PaymentFlowDetail flow = paymentFlowService.flowForReference(reference);
            diagnosticStages = markRunningAsTimeout(mergeStages(earlyStages, flow.stages()));
        } else {
            diagnosticStages = markRunningAsTimeout(fillPending(earlyStages));
        }
        String diagnostic = lastKnown.isPresent()
                ? "Payment row found (status=" + lastKnown.get().status() + ") but never reached a terminal status within "
                + properties.getE2e().getMaxDurationSeconds() + "s."
                : "No payment row appeared for reference " + reference + " within " + properties.getE2e().getMaxDurationSeconds()
                + "s - check payment-service's Kafka consumer.";
        finish(runId, reference, correlationId, traceId, startedAt, E2EStageStatus.TIMEOUT, diagnostic, diagnosticStages);
    }

    /** Overlays this backend's own known Gateway/Authentication/Validation results on top of PaymentFlowService's real, independently-computed Routing-through-Reporting stages. */
    private List<E2EStage> mergeStages(List<E2EStage> earlyStages, List<PaymentFlowStage> flowStages) {
        List<E2EStage> merged = new ArrayList<>(earlyStages);
        for (PaymentFlowStage stage : flowStages) {
            if (STAGE_NAMES.subList(0, 3).contains(stage.stage())) continue; // Gateway/Authentication/Validation stay E2E-known
            merged.add(new E2EStage(stage.stage(), mapStatus(stage.status()), stage.detail(), stage.occurredAt()));
        }
        return merged;
    }

    private E2EStageStatus mapStatus(com.paymentx.controlcenter.dto.postgres.PaymentFlowStageStatus status) {
        return switch (status) {
            case COMPLETED -> E2EStageStatus.SUCCESS;
            case PROCESSING -> E2EStageStatus.RUNNING;
            case FAILED -> E2EStageStatus.FAILED;
            case NOT_STARTED, UNAVAILABLE -> E2EStageStatus.PENDING;
        };
    }

    private List<E2EStage> fillPending(List<E2EStage> knownSoFar) {
        List<E2EStage> result = new ArrayList<>(knownSoFar);
        for (String name : STAGE_NAMES.subList(knownSoFar.size(), STAGE_NAMES.size())) {
            result.add(new E2EStage(name, E2EStageStatus.PENDING, "Not reached yet.", null));
        }
        return result;
    }

    private List<E2EStage> markRunningAsTimeout(List<E2EStage> stages) {
        List<E2EStage> result = new ArrayList<>();
        for (E2EStage stage : stages) {
            if (stage.status() == E2EStageStatus.RUNNING || stage.status() == E2EStageStatus.PENDING) {
                result.add(new E2EStage(stage.name(), E2EStageStatus.TIMEOUT, stage.detail(), stage.occurredAt()));
            } else {
                result.add(stage);
            }
        }
        return result;
    }

    private E2EStage stageOk(String name, String detail) {
        return new E2EStage(name, E2EStageStatus.SUCCESS, detail, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private E2EStage stageFailed(String name, String detail) {
        return new E2EStage(name, E2EStageStatus.FAILED, detail, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private E2ERunResult snapshot(String runId, String reference, String correlationId, String traceId,
                                   E2EStageStatus overallStatus, OffsetDateTime startedAt, OffsetDateTime completedAt,
                                   List<E2EStage> stages) {
        long duration = (completedAt != null ? completedAt : OffsetDateTime.now(ZoneOffset.UTC)).toInstant().toEpochMilli()
                - startedAt.toInstant().toEpochMilli();
        return new E2ERunResult(runId, reference, correlationId, traceId, overallStatus, startedAt, completedAt, duration, null, stages);
    }

    private void finish(String runId, String reference, String correlationId, String traceId, OffsetDateTime startedAt,
                         E2EStageStatus overallStatus, String failureReason, List<E2EStage> stages) {
        OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
        long duration = completedAt.toInstant().toEpochMilli() - startedAt.toInstant().toEpochMilli();
        E2ERunResult result = new E2ERunResult(runId, reference, correlationId, traceId, overallStatus, startedAt, completedAt, duration, failureReason, stages);
        putRun(result);
        log.info("E2E run {} finished status={} reference={} durationMs={}", runId, overallStatus, reference, duration);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ControlCenterException("E2E_INTERRUPTED", "E2E run was interrupted.", e);
        }
    }
}
