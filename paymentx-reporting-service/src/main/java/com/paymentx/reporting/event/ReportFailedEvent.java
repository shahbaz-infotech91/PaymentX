package com.paymentx.reporting.event;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Published when a report execution fails - an operator-
 * facing alerting signal, separate from ReportGeneratedEvent's success
 * path.
 *
 * HINGLISH: Jab ek report execution fail hota hai tab publish hota hai -
 * ek operator-facing alerting signal, ReportGeneratedEvent ke success
 * path se alag.
 * ====================================================================
 */
@Getter
@Builder
public class ReportFailedEvent {
    private UUID reportExecutionId;
    private String reportType;
    private String failureReason;
}
