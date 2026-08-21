package com.paymentx.notification.dto;

import com.paymentx.notification.entity.NotificationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * DeliveryAttemptResponse is a record (DTO) in the notification module of PaymentX. It lives in package com.paymentx.notification.dto and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * DeliveryAttemptResponse PaymentX ke notification module ka ek record (DTO) hai. Ye com.paymentx.notification.dto package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record DeliveryAttemptResponse(
        UUID id,
        Integer attemptNumber,
        NotificationStatus status,
        String errorMessage,
        Integer responseCode,
        OffsetDateTime attemptedAt
) {
}
