package com.paymentx.common.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * ISO-8601 date/time helpers shared across services. {@code Instant} is
 * used for wire/event timestamps (matches
 * {@link com.paymentx.common.event.PaymentEvent#getOccurredAt()});
 * {@code OffsetDateTime} for persisted entity timestamps (matches
 * {@link com.paymentx.common.base.AuditableEntity}'s {@code createdAt}/
 * {@code updatedAt} and Validation Service's {@code ValidationLog.validatedAt}
 * pattern) - kept as two distinct types rather than picking one, since
 * that split already exists and is intentional in this codebase.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * DateUtils is a class in the common module of PaymentX. It lives in package com.paymentx.common.util and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * DateUtils PaymentX ke common module ka ek class hai. Ye com.paymentx.common.util package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class DateUtils {

    public static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;
    public static final DateTimeFormatter ISO_OFFSET_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private DateUtils() {}

    public static Instant nowUtc() {
        return Instant.now();
    }

    public static OffsetDateTime nowOffsetUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public static String toIso(Instant instant) {
        return instant == null ? null : ISO_INSTANT.format(instant);
    }

    public static String toIso(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : ISO_OFFSET_DATE_TIME.format(offsetDateTime);
    }

    public static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    public static OffsetDateTime parseOffsetDateTime(String value) {
        return value == null || value.isBlank() ? null : OffsetDateTime.parse(value, ISO_OFFSET_DATE_TIME);
    }

    public static boolean isValidIsoInstant(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
