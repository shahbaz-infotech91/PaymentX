package com.paymentx.controlcenter.dto.e2e;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ENGLISH: The full, real state of one E2E run - both the live
 * snapshot a poll returns while it's still RUNNING and the final
 * record kept in the bounded run history once it finishes. paymentReference
 * is a real, freshly-generated reference this run actually submitted to
 * the real API Gateway (never reused across runs); correlationId is
 * the real X-Correlation-Id this backend generated and sent on that
 * real request; traceId is the real value the real ValidationResponse
 * body returned (null until that response arrives). overallStatus is
 * only ever SUCCESS when the real payment reached a real terminal
 * SETTLED status - E2EFlowService never sets SUCCESS on a timeout or a
 * partial result. durationMillis is measured from this run's own real
 * start time, either to its real completion or, for an in-flight run,
 * to "now".
 *
 * HINGLISH: Ek E2E run ka poora, real state - dono, ek live snapshot
 * jo ek poll return karta hai jab tak wo abhi RUNNING hai, aur wo final
 * record jo bounded run history me rakha jaata hai jab wo finish ho
 * jaaye. paymentReference ek real, freshly-generated reference hai jo
 * is run ne actually real API Gateway ko submit kiya (kabhi runs ke
 * across reuse nahi hota); correlationId wo real X-Correlation-Id hai
 * jo is backend ne generate kiya aur us real request par bheja;
 * traceId wo real value hai jo real ValidationResponse body ne return
 * ki (jab tak wo response na aaye null rehta hai). overallStatus kabhi
 * bhi SUCCESS tabhi hota hai jab real payment ek real terminal SETTLED
 * status tak pahunche - E2EFlowService kabhi timeout ya partial result
 * par SUCCESS set nahi karta. durationMillis is run ke apne real start
 * time se measure hota hai, ya toh uske real completion tak, ya ek
 * in-flight run ke liye "abhi" tak.
 */
public record E2ERunResult(
        String runId,
        String paymentReference,
        String correlationId,
        String traceId,
        E2EStageStatus overallStatus,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        long durationMillis,
        String failureReason,
        List<E2EStage> stages
) {
}
