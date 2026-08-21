package com.paymentx.payment.util;

import com.paymentx.payment.filter.CorrelationIdFilter;
import org.slf4j.MDC;

/**
 * WHY this utility exists: requirement #18 says every log line must carry
 * correlationId + paymentId + participantId. For HTTP-triggered requests,
 * this service's own CorrelationIdFilter (com.paymentx.payment.filter -
 * moved out of paymentx-common-library during the architecture cleanup
 * that removed all servlet dependencies from the shared library) already
 * populates correlationId into MDC automatically via a servlet filter.
 * But Kafka-consumer-triggered flows (PaymentValidatedConsumer, the
 * schedulers) have NO HTTP request in the loop - that filter never runs
 * for them. This class is the Kafka/background-job equivalent: called
 * explicitly at the start of a consumer/scheduler method, cleared in a
 * finally block, exactly mirroring CorrelationIdFilter's try/finally MDC
 * discipline for the same reason (pooled threads must never leak one
 * execution's context into the next).
 *
 * WHY reuse CorrelationIdFilter.MDC_KEY rather than declaring our own
 * "correlationId" string here: a single source of truth for the MDC key
 * name means the logging pattern configured in application.yml
 * (%X{correlationId}) works identically whether the log line originated
 * from an HTTP request or a Kafka consumer - one log format, one
 * correlation key, regardless of trigger source.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * LoggingContext is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.util and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * LoggingContext PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.util package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class LoggingContext {

    public static final String CORRELATION_ID_KEY = CorrelationIdFilter.MDC_KEY;
    public static final String TRACE_ID_KEY = "traceId";
    public static final String PAYMENT_ID_KEY = "paymentId";
    public static final String PARTICIPANT_ID_KEY = "participantId";

    private LoggingContext() {}

    public static void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
    }

    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    public static void setPaymentId(String paymentId) {
        if (paymentId != null) {
            MDC.put(PAYMENT_ID_KEY, paymentId);
        }
    }

    public static void setParticipantId(String participantId) {
        if (participantId != null) {
            MDC.put(PARTICIPANT_ID_KEY, participantId);
        }
    }

    /**
     * Always call from a finally block - see CorrelationIdFilter's javadoc
     * for the pooled-thread context-leak explanation this mirrors.
     */
    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(PAYMENT_ID_KEY);
        MDC.remove(PARTICIPANT_ID_KEY);
    }
}
