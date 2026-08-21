package com.paymentx.reporting.event;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH: Published when a report's EXPORT (not just generation)
 * finishes - distinct from ReportGeneratedEvent because a report can be
 * generated once but exported to multiple formats independently; this
 * fires per-export-completion.
 *
 * HINGLISH: Jab ek report ka EXPORT (sirf generation nahi) khatam hota
 * hai tab publish hota hai - ReportGeneratedEvent se alag kyunki ek
 * report ek baar generate ho sakta hai lekin multiple formats me
 * independently export ho sakta hai.
 * ====================================================================
 */
@Getter
@Builder
public class ReportCompletedEvent {
    private UUID reportExecutionId;
    private String format;
    private String storagePath;
}
