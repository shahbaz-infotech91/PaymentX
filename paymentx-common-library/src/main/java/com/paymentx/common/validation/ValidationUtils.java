package com.paymentx.common.validation;

import com.paymentx.common.constant.RegexConstants;
import com.paymentx.common.exception.ValidationException;

import java.util.regex.Pattern;

/**
 * Generic, reusable field validators - shape/format checks only (blank,
 * UUID, email, regex match), never business rules (those belong to
 * {@code exception.BusinessException} and stay service-local) and never
 * domain-specific "is this a currency/scheme WE support" checks (that
 * would require exactly the kind of business-domain model - a
 * {@code Currency} enum, a {@code PaymentScheme} - ADR 0004 excludes from
 * this library). {@link #isValidIsoCurrencyCodeFormat(String)} validates
 * only that a string LOOKS like an ISO 4217 code (3 uppercase letters),
 * not that it names a currency the platform actually processes.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationUtils is a class in the common module of PaymentX. It lives in package com.paymentx.common.validation and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationUtils PaymentX ke common module ka ek class hai. Ye com.paymentx.common.validation package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /** Throws {@link ValidationException} if {@code value} is blank; returns it unchanged otherwise, so this composes at an assignment/argument site. */
    public static String requireNonBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new ValidationException(fieldName + " must not be blank");
        }
        return value;
    }

    public static boolean matches(String value, Pattern pattern) {
        return value != null && pattern.matcher(value).matches();
    }

    public static boolean isValidUuid(String value) {
        return matches(value, RegexConstants.UUID_PATTERN);
    }

    public static boolean isValidEmail(String value) {
        return matches(value, RegexConstants.EMAIL_PATTERN);
    }

    /** Shape check only - see class javadoc. */
    public static boolean isValidIsoCurrencyCodeFormat(String value) {
        return matches(value, RegexConstants.ISO_CURRENCY_CODE_PATTERN);
    }

    /** Shape check only - see class javadoc. */
    public static boolean isValidIsoCountryCodeFormat(String value) {
        return matches(value, RegexConstants.ISO_COUNTRY_CODE_PATTERN);
    }

    public static boolean isValidCorrelationId(String value) {
        return matches(value, RegexConstants.CORRELATION_ID_PATTERN);
    }

    public static boolean isInRange(int value, int minInclusive, int maxInclusive) {
        return value >= minInclusive && value <= maxInclusive;
    }

    public static boolean hasMaxLength(String value, int maxLength) {
        return value != null && value.length() <= maxLength;
    }
}
