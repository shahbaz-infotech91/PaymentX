package com.paymentx.reporting.service.impl;

import com.paymentx.reporting.entity.ReportExecution;
import com.paymentx.reporting.entity.ReportMetadata;
import com.paymentx.reporting.entity.ReportRequest;
import com.paymentx.reporting.entity.ReportResult;
import com.paymentx.reporting.entity.ReportStatus;
import com.paymentx.reporting.event.ReportFailedApplicationEvent;
import com.paymentx.reporting.event.ReportFailedEvent;
import com.paymentx.reporting.event.ReportGeneratedApplicationEvent;
import com.paymentx.reporting.event.ReportGeneratedEvent;
import com.paymentx.reporting.metrics.ReportingMetrics;
import com.paymentx.reporting.repository.ReportExecutionRepository;
import com.paymentx.reporting.repository.ReportMetadataRepository;
import com.paymentx.reporting.repository.ReportRequestRepository;
import com.paymentx.reporting.repository.ReportResultRepository;
import com.paymentx.reporting.service.generator.ReportGeneratorFactory;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH:
 * The actual transactional report-generation work, split out of
 * ReportGenerationProcessor into its OWN bean for the same reason
 * @Async needs a separate bean: Spring's @Transactional proxying only
 * intercepts calls arriving THROUGH the proxy. ReportGenerationProcessor.
 * generateAsync() used to call generate() on `this` (a same-class,
 * same-instance call) - that self-invocation completely bypassed the
 * @Transactional(REQUIRES_NEW) advice, so every repository call here
 * silently ran in its OWN independent auto-opened transaction instead
 * of one shared REQUIRES_NEW transaction. The first save() (status=
 * RUNNING) committed and closed its own transaction/persistence
 * context; the in-memory `execution` object was never refreshed with
 * the post-commit version, so the second save() (status=COMPLETED/
 * FAILED) always merged a now-stale version against the DB and threw
 * ObjectOptimisticLockingFailureException - which then made even the
 * catch block's own FAILED-status save fail the same way, leaving the
 * row stuck at RUNNING forever. Calling run() on this separate,
 * injected bean forces the call through the real Spring proxy so
 * REQUIRES_NEW is genuinely applied.
 *
 * HINGLISH:
 * Actual transactional report-generation kaam, ReportGenerationProcessor
 * se alag NIKAAL kar apne bean me rakha gaya hai - wahi reason jo @Async
 * ke liye alag bean maangta hai: Spring ka @Transactional proxying sirf
 * un calls ko intercept karta hai jo PROXY ke through aati hain.
 * ReportGenerationProcessor.generateAsync() pehle generate() ko `this`
 * pe call karta tha (same-class, same-instance call) - ye self-invocation
 * @Transactional(REQUIRES_NEW) advice ko poori tarah bypass kar deta tha,
 * isliye yahan har repository call apne alag, auto-open hue transaction
 * me chalti thi, ek shared REQUIRES_NEW transaction me nahi. Pehla save()
 * (status=RUNNING) apna transaction/persistence-context commit karke band
 * kar deta tha; in-memory `execution` object kabhi post-commit version se
 * refresh nahi hota tha, isliye doosra save() (status=COMPLETED/FAILED)
 * hamesha ek stale version ko DB ke against merge karta aur
 * ObjectOptimisticLockingFailureException throw karta - jisse catch
 * block ka apna FAILED-status save bhi waisi hi galti se fail ho jaata,
 * aur row hamesha RUNNING pe atka reh jaata. Is alag, injected bean pe
 * run() call karne se call asli Spring proxy ke through jaati hai taaki
 * REQUIRES_NEW sach me apply ho.
 * ====================================================================
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationRunner {

    private final ReportRequestRepository reportRequestRepository;
    private final ReportExecutionRepository reportExecutionRepository;
    private final ReportResultRepository reportResultRepository;
    private final ReportMetadataRepository reportMetadataRepository;
    private final ReportGeneratorFactory reportGeneratorFactory;
    private final ReportingMetrics reportingMetrics;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(UUID executionId) {
        Timer.Sample timerSample = reportingMetrics.startTimer();
        ReportExecution execution = reportExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("ReportExecution not found at generation time: " + executionId));

        ReportRequest request = reportRequestRepository.findById(execution.getReportRequestId())
                .orElseThrow(() -> new IllegalStateException("ReportRequest not found: " + execution.getReportRequestId()));

        execution.setStatus(ReportStatus.RUNNING);
        execution.setStartedAt(OffsetDateTime.now());
        reportExecutionRepository.save(execution);

        try {
            var generator = reportGeneratorFactory.getGenerator(request.getReportType())
                    .orElseThrow(() -> new IllegalStateException("No generator registered for report type: " + request.getReportType()));

            String resultJson = generator.generate(request);

            ReportResult result = ReportResult.builder()
                    .reportExecutionId(executionId)
                    .resultData(resultJson)
                    .build();
            reportResultRepository.save(result);

            ReportMetadata metadata = ReportMetadata.builder()
                    .reportExecutionId(executionId)
                    .sourceServices(request.getReportType().name())
                    .dataAsOf(OffsetDateTime.now())
                    .generatorVersion("1.0")
                    .build();
            reportMetadataRepository.save(metadata);

            execution.setStatus(ReportStatus.COMPLETED);
            execution.setCompletedAt(OffsetDateTime.now());
            execution.setRowCount(estimateRowCount(resultJson));
            execution.setGenerationTimeMillis(Duration.between(execution.getStartedAt(), execution.getCompletedAt()).toMillis());
            reportExecutionRepository.save(execution);

            reportingMetrics.recordGenerationDuration(timerSample, request.getReportType().name());
            applicationEventPublisher.publishEvent(new ReportGeneratedApplicationEvent(this,
                    ReportGeneratedEvent.builder()
                            .reportExecutionId(executionId)
                            .reportType(request.getReportType().name())
                            .rowCount(execution.getRowCount())
                            .build(),
                    request.getCorrelationId()));

            log.info("Report generation completed executionId={} reportType={}", executionId, request.getReportType());

        } catch (Exception e) {
            log.error("Report generation failed executionId={}", executionId, e);
            execution.setStatus(ReportStatus.FAILED);
            execution.setFailureReason(e.getMessage());
            execution.setCompletedAt(OffsetDateTime.now());
            execution.setRetryCount(execution.getRetryCount() + 1);
            reportExecutionRepository.save(execution);

            reportingMetrics.recordFailure(request.getReportType().name());
            applicationEventPublisher.publishEvent(new ReportFailedApplicationEvent(this,
                    ReportFailedEvent.builder()
                            .reportExecutionId(executionId)
                            .reportType(request.getReportType().name())
                            .failureReason(e.getMessage())
                            .build(),
                    request.getCorrelationId()));
        }
    }

    /** WHY a rough character-count heuristic, not a real parse-and-count:
     *  rowCount here is an informational metric, not used for any
     *  business decision - a lightweight estimate is sufficient and
     *  avoids a second full JSON parse right after generation already
     *  parsed it once internally. */
    private Integer estimateRowCount(String resultJson) {
        return resultJson == null ? 0 : (int) resultJson.chars().filter(c -> c == '{').count();
    }
}
