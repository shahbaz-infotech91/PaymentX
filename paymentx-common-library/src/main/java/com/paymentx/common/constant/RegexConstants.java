package com.paymentx.common.constant;

import java.util.regex.Pattern;

/**
 * Shared regular expressions used across services.
 *
 * NOTE:
 * This class contains only generic format validations.
 * Business-specific validations must stay inside individual services.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RegexConstants is a class in the common module of PaymentX. It lives in package com.paymentx.common.constant and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RegexConstants PaymentX ke common module ka ek class hai. Ye com.paymentx.common.constant package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class RegexConstants {

    private RegexConstants() {
    }

    // =========================
    // Regex Strings
    // =========================

    /** ISO 4217 currency code (e.g. INR, USD, EUR) */
    public static final String CURRENCY_CODE = "^[A-Z]{3}$";

    /** ISO 3166-1 alpha-2 country code (e.g. IN, US, GB) */
    public static final String COUNTRY_CODE = "^[A-Z]{2}$";

    /** UUID */
    public static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** Email */
    public static final String EMAIL =
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";

    /** E.164 Phone Number */
    public static final String PHONE_E164 =
            "^\\+[1-9]\\d{1,14}$";

    /** Alphanumeric */
    public static final String ALPHANUMERIC =
            "^[A-Za-z0-9]+$";

    /** Correlation Id (UUID format) */
    public static final String CORRELATION_ID =
            UUID;

    // =========================
    // Compiled Patterns
    // =========================

    public static final Pattern ISO_CURRENCY_CODE_PATTERN =
            Pattern.compile(CURRENCY_CODE);

    public static final Pattern ISO_COUNTRY_CODE_PATTERN =
            Pattern.compile(COUNTRY_CODE);

    public static final Pattern UUID_PATTERN =
            Pattern.compile(UUID);

    public static final Pattern EMAIL_PATTERN =
            Pattern.compile(EMAIL);

    public static final Pattern PHONE_E164_PATTERN =
            Pattern.compile(PHONE_E164);

    public static final Pattern ALPHANUMERIC_PATTERN =
            Pattern.compile(ALPHANUMERIC);

    public static final Pattern CORRELATION_ID_PATTERN =
            Pattern.compile(CORRELATION_ID);
}