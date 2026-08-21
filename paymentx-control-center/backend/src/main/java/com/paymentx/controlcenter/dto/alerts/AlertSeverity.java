package com.paymentx.controlcenter.dto.alerts;

/**
 * ENGLISH: How urgent a real, detected alert condition is. CRITICAL =
 * something is actually down/unreachable right now (a service, a
 * database, Redis, RabbitMQ). WARNING = a real metric crossed a fixed,
 * documented threshold (error rate, latency, Kafka lag, queue
 * buildup, payment failure rate, reconciliation mismatches) but the
 * component itself is still up.
 *
 * HINGLISH: Ek real, detect ki gayi alert condition kitni urgent hai.
 * CRITICAL = kuch actually abhi down/unreachable hai (ek service, ek
 * database, Redis, RabbitMQ). WARNING = ek real metric ek fixed,
 * documented threshold cross kar gaya (error rate, latency, Kafka
 * lag, queue buildup, payment failure rate, reconciliation
 * mismatches) lekin component khud abhi bhi up hai.
 */
public enum AlertSeverity {
    CRITICAL,
    WARNING
}
