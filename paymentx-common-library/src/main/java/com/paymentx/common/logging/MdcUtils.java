package com.paymentx.common.logging;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Low-level, key-agnostic MDC helpers. {@link LoggingContext} builds the
 * platform's fixed set of well-known keys (correlationId, traceId, userId)
 * on top of this class; this class itself has no opinion on key names and
 * is safe to use directly for any service-specific key (e.g. Payment
 * Service's {@code paymentId}/{@code participantId}) that doesn't belong
 * in a cross-cutting shared class per ADR 0004's business-domain boundary.
 *
 * <p>{@link #runWithContext} is the piece a plain put/remove pair can't
 * give you safely: it snapshots whatever was already in MDC for the given
 * keys, runs the block, then restores exactly that snapshot afterward -
 * not a blind {@code MDC.remove}. That distinction matters on a pooled
 * thread (Kafka consumer thread, {@code @Async} executor) that might
 * already be carrying an outer context you must not clobber for the
 * remainder of that thread's work.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MdcUtils is a class in the common module of PaymentX. It lives in package com.paymentx.common.logging and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MdcUtils PaymentX ke common module ka ek class hai. Ye com.paymentx.common.logging package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class MdcUtils {

    private MdcUtils() {}

    public static void put(String key, String value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    public static String get(String key) {
        return key == null ? null : MDC.get(key);
    }

    public static void remove(String key) {
        if (key != null) {
            MDC.remove(key);
        }
    }

    /** Runs {@code action}, temporarily overlaying {@code context} onto the current MDC, then restores each key to its prior value (or removes it if it was previously absent). */
    public static <T> T runWithContext(Map<String, String> context, Supplier<T> action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (context != null) {
                context.forEach(MdcUtils::put);
            }
            return action.get();
        } finally {
            restore(previous, context);
        }
    }

    /** {@code void}-returning overload of {@link #runWithContext(Map, Supplier)} for actions with no result. */
    public static void runWithContext(Map<String, String> context, Runnable action) {
        runWithContext(context, () -> {
            action.run();
            return null;
        });
    }

    private static void restore(Map<String, String> previous, Map<String, String> overlaid) {
        if (overlaid == null) {
            return;
        }
        overlaid.keySet().forEach(key -> {
            String previousValue = previous == null ? null : previous.get(key);
            put(key, previousValue);
        });
    }
}
