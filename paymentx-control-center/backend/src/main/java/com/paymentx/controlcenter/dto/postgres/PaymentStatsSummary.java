package com.paymentx.controlcenter.dto.postgres;

/**
 * ENGLISH: The Home page's real, aggregate payment-lifecycle
 * snapshot - computed with one real `GROUP BY status, COUNT(*)` query
 * against paymentx_payment.payment, then bucketed in Java using the
 * real, verified PaymentStatus state machine from payment-service's
 * own entity/PaymentStatus.java (RECEIVED/VALIDATED -> pending;
 * PROCESSING/ROUTING/DEBITING/CREDITING/SETTLING/RETRYING ->
 * processing; SETTLED -> successful; DEBIT_FAILED/CREDIT_FAILED/
 * FAILED/TIMEOUT/CANCELLED/RETURNED/REVERSED -> failed) - never an
 * invented classification. successRatePercent/failureRatePercent are
 * real percentages of totalPayments; averageLatencyMillis is a real
 * AVG(updated_at - created_at) over payments that have actually
 * reached a terminal state (in-flight payments would understate
 * latency if included); paymentsLastHour/tps are a real, live
 * COUNT(*) WHERE created_at >= now() - 1 hour, divided by 3600 for
 * tps - a genuine throughput signal that works even when Prometheus
 * has no current scrape data.
 *
 * HINGLISH: Home page ka real, aggregate payment-lifecycle snapshot -
 * paymentx_payment.payment ke against ek real `GROUP BY status,
 * COUNT(*)` query se compute kiya gaya, phir Java me payment-service
 * ke apne, real, verified PaymentStatus state machine
 * (entity/PaymentStatus.java) use karke bucket kiya gaya
 * (RECEIVED/VALIDATED -> pending; PROCESSING/ROUTING/DEBITING/
 * CREDITING/SETTLING/RETRYING -> processing; SETTLED -> successful;
 * DEBIT_FAILED/CREDIT_FAILED/FAILED/TIMEOUT/CANCELLED/RETURNED/
 * REVERSED -> failed) - kabhi ek invented classification nahi.
 * successRatePercent/failureRatePercent totalPayments ke real
 * percentages hain; averageLatencyMillis un payments par ek real
 * AVG(updated_at - created_at) hai jo actually ek terminal state tak
 * pahunche hain (in-flight payments shamil karne se latency understate
 * ho jaati); paymentsLastHour/tps ek real, live COUNT(*) WHERE
 * created_at >= now() - 1 hour hai, tps ke liye 3600 se divide kiya
 * gaya - ek genuine throughput signal jo tab bhi kaam karta hai jab
 * Prometheus ke paas koi current scrape data na ho.
 */
public record PaymentStatsSummary(
        long totalPayments,
        long successful,
        long failed,
        long pending,
        long processing,
        double successRatePercent,
        double failureRatePercent,
        Double averageLatencyMillis,
        long paymentsLastHour,
        double tps
) {
}
