package com.paymentx.common.security;

/**
 * The actual masking algorithms backing {@link MaskStrategy}. Kept as a
 * plain, dependency-free static utility (rather than only living inside
 * the Jackson-specific {@code annotation.MaskingSerializer}) so any code
 * that needs to mask a value BEFORE it reaches Jackson at all - most
 * importantly, a log statement, since {@code @Mask} on a DTO field only
 * protects JSON serialization, never a stray {@code log.info("account={}", account)}
 * - can call this directly.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * DataMaskingUtils is a class in the common module of PaymentX. It lives in package com.paymentx.common.security and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * DataMaskingUtils PaymentX ke common module ka ek class hai. Ye com.paymentx.common.security package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class DataMaskingUtils {

    private static final String MASK_CHAR = "*";
    private static final int DEFAULT_VISIBLE_CHARS = 4;

    private DataMaskingUtils() {}

    public static String mask(String value, MaskStrategy strategy) {
        return mask(value, strategy, DEFAULT_VISIBLE_CHARS);
    }

    public static String mask(String value, MaskStrategy strategy, int visibleChars) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (strategy) {
            case FULL -> MASK_CHAR.repeat(value.length());
            case PARTIAL -> maskPartial(value, visibleChars);
            case EMAIL -> maskEmail(value);
        };
    }

    private static String maskPartial(String value, int visibleChars) {
        int visible = Math.max(0, Math.min(visibleChars, value.length()));
        int maskedLength = value.length() - visible;
        return MASK_CHAR.repeat(maskedLength) + value.substring(maskedLength);
    }

    private static String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex <= 0) {
            // Not a recognizable "local@domain" shape - fail safe by fully masking rather than leaking it verbatim.
            return MASK_CHAR.repeat(value.length());
        }
        String localPart = value.substring(0, atIndex);
        String domain = value.substring(atIndex);
        String maskedLocal = localPart.charAt(0) + MASK_CHAR.repeat(Math.max(1, localPart.length() - 1));
        return maskedLocal + domain;
    }
}
