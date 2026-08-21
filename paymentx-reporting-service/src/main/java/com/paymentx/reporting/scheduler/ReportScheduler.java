package com.paymentx.reporting.scheduler;

import com.paymentx.reporting.dto.GenerateReportRequest;
import com.paymentx.reporting.entity.ReportExecution;
import com.paymentx.reporting.entity.ReportSchedule;
import com.paymentx.reporting.entity.ReportStatus;
import com.paymentx.reporting.repository.ReportExecutionRepository;
import com.paymentx.reporting.repository.ReportScheduleRepository;
import com.paymentx.reporting.service.ReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH:
 * Two jobs, matching the explicit "Daily/Weekly/Monthly/Yearly Reports,
 * Manual Execution, Retry Failed Reports" requirement: (1) checks every
 * active ReportSchedule once a minute and fires a generation for any
 * schedule whose own cron says it's due, and (2) retries every FAILED
 * execution under its retry budget on a separate, slower cadence.
 *
 * HINGLISH:
 * Do jobs, explicit "Daily/Weekly/Monthly/Yearly Reports, Manual
 * Execution, Retry Failed Reports" requirement ke hisab se: (1) har
 * active ReportSchedule ko minute me ek baar check karta hai aur jis
 * schedule ka apna cron kehta hai ki ab due hai uska generation fire
 * karta hai, aur (2) har FAILED execution ko uske retry budget ke andar
 * ek alag, slower cadence pe retry karta hai.
 * ====================================================================
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduler {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long POLL_FIXED_DELAY_MILLIS = 60_000L;
    private static final long RETRY_FIXED_DELAY_MILLIS = 300_000L;

    private final ReportScheduleRepository reportScheduleRepository;
    private final ReportExecutionRepository reportExecutionRepository;
    private final ReportingService reportingService;

    @Scheduled(fixedDelay = POLL_FIXED_DELAY_MILLIS)
    @Transactional
    public void pollDueSchedules() {
        var activeSchedules = reportScheduleRepository.findByActiveTrue();
        OffsetDateTime now = OffsetDateTime.now();

        for (ReportSchedule schedule : activeSchedules) {
            if (isDue(schedule, now)) {
                fireSchedule(schedule, now);
            }
        }
    }

    @Scheduled(fixedDelay = RETRY_FIXED_DELAY_MILLIS)
    @Transactional
    public void retryFailedReports() {
        var failedExecutions = reportExecutionRepository.findByStatus(ReportStatus.FAILED);
        for (ReportExecution execution : failedExecutions) {
            if (execution.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                log.info("Retrying failed report execution id={} attempt={}", execution.getId(), execution.getRetryCount() + 1);
                execution.setStatus(ReportStatus.PENDING);
                reportExecutionRepository.save(execution);
            }
        }
    }

    /** WHY a Spring CronExpression parse+match, not a naive
     *  "has it been 24h since lastRunAt" check: the explicit cron
     *  expression on ReportSchedule must be honoured literally -
     *  CronExpression.next() is the correct, standard way to answer
     *  "is this schedule due right now." */
    private boolean isDue(ReportSchedule schedule, OffsetDateTime now) {
        try {
            CronExpression cron = CronExpression.parse(schedule.getCronExpression());
            OffsetDateTime referencePoint = schedule.getLastRunAt() != null ? schedule.getLastRunAt() : schedule.getCreatedAt();
            OffsetDateTime next = cron.next(referencePoint);
            return next != null && !next.isAfter(now);
        } catch (Exception e) {
            log.warn("Invalid cron expression for scheduleId={} cron={}", schedule.getId(), schedule.getCronExpression(), e);
            return false;
        }
    }

    private void fireSchedule(ReportSchedule schedule, OffsetDateTime now) {
        GenerateReportRequest request = new GenerateReportRequest(
                schedule.getReportType(), schedule.getReportFormat(),
                now.minusDays(1), now, null, null, null);

        reportingService.generateReport(request, "SCHEDULER", null);

        schedule.setLastRunAt(now);
        reportScheduleRepository.save(schedule);
        log.info("Scheduled report fired scheduleId={} reportType={}", schedule.getId(), schedule.getReportType());
    }
}
