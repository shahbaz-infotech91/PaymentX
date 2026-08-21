package com.paymentx.notification.dto;

import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.NotificationStatus;
import com.paymentx.notification.entity.SourceEventType;

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationSearchCriteria is a record (DTO) in the notification module of PaymentX. It lives in package com.paymentx.notification.dto and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationSearchCriteria PaymentX ke notification module ka ek record (DTO) hai. Ye com.paymentx.notification.dto package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record NotificationSearchCriteria(
        NotificationStatus status,
        NotificationChannel channel,
        SourceEventType sourceEventType,
        String recipient,
        String participantId,
        OffsetDateTime fromDate,
        OffsetDateTime toDate
) {
}
