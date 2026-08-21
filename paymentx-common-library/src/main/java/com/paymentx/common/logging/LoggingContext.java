package com.paymentx.common.logging;

/**
 * The platform's fixed, well-known MDC keys, generalized from the pattern
 * already proven by Payment Service's local {@code util.LoggingContext}
 * (which layers {@code paymentId}/{@code participantId} on top of
 * {@link CorrelationIdFilter}'s correlation key for its Kafka-consumer and
 * scheduler flows, which never go through the servlet filter). This
 * shared version keeps only the keys that are truly cross-cutting -
 * {@code correlationId} (transport-hop identifier, set by
 * {@link CorrelationIdFilter} for HTTP or manually for Kafka/background
 * flows) and {@code traceId} (business/domain trace identifier - the same
 * concept as {@link com.paymentx.common.event.PaymentEvent#getTraceId()},
 * generated once at the origin of a business transaction and carried
 * through every downstream event and log line) - plus {@code userId} for
 * services with an authenticated caller.
 *
 * <p>Service-specific keys (a {@code paymentId}, a {@code participantId})
 * are NOT hardcoded here - per ADR 0004's business-domain boundary, this
 * library stays domain-agnostic. Use {@link #put(String, String)} /
 * {@link MdcUtils#put(String, String)} directly for those; the logging
 * pattern in each service's {@code logback-spring.xml} can reference any
 * MDC key by name regardless of which class put it there.
 *
 * <p>ALWAYS pair a {@code setXxx} call with the matching {@code clearXxx}
 * (or {@link #clear()}) in a {@code finally} block - see
 * {@link CorrelationIdFilter} for why: threads are pooled and reused, and
 * a leaked MDC entry silently attaches to the next unrelated
 * request/message handled by that thread.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * LoggingContext is a class in the common module of PaymentX. It lives in package com.paymentx.common.logging and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * LoggingContext PaymentX ke common module ka ek class hai. Ye com.paymentx.common.logging package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class LoggingContext {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String TRACE_ID_KEY = "traceId";
    public static final String USER_ID_KEY = "userId";

    private LoggingContext() {}

    public static void setCorrelationId(String correlationId) {
        MdcUtils.put(CORRELATION_ID_KEY, correlationId);
    }

    public static String getCorrelationId() {
        return MdcUtils.get(CORRELATION_ID_KEY);
    }

    public static void clearCorrelationId() {
        MdcUtils.remove(CORRELATION_ID_KEY);
    }

    public static void setTraceId(String traceId) {
        MdcUtils.put(TRACE_ID_KEY, traceId);
    }

    public static String getTraceId() {
        return MdcUtils.get(TRACE_ID_KEY);
    }

    public static void clearTraceId() {
        MdcUtils.remove(TRACE_ID_KEY);
    }

    public static void setUserId(String userId) {
        MdcUtils.put(USER_ID_KEY, userId);
    }

    public static String getUserId() {
        return MdcUtils.get(USER_ID_KEY);
    }

    public static void clearUserId() {
        MdcUtils.remove(USER_ID_KEY);
    }

    /** Escape hatch for a service-specific key that doesn't warrant a dedicated accessor - see class javadoc. */
    public static void put(String key, String value) {
        MdcUtils.put(key, value);
    }

    /** Clears exactly the platform's well-known keys ({@link #CORRELATION_ID_KEY}, {@link #TRACE_ID_KEY}, {@link #USER_ID_KEY}) - never a blanket {@code MDC.clear()}, which could wipe keys other code on the same thread still needs. */
    public static void clear() {
        clearCorrelationId();
        clearTraceId();
        clearUserId();
    }
}
