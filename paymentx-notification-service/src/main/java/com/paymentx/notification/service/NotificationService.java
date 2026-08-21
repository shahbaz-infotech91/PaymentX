package com.paymentx.notification.service;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.notification.dto.DeliveryAttemptResponse;
import com.paymentx.notification.dto.NotificationResponse;
import com.paymentx.notification.dto.NotificationSearchCriteria;
import com.paymentx.notification.dto.ResendNotificationRequest;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.SourceEventType;

import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationService is a interface in the notification module of PaymentX. It lives in package com.paymentx.notification.service and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationService PaymentX ke notification module ka ek interface hai. Ye com.paymentx.notification.service package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface NotificationService {

    /** Kafka-sourced creation - idempotent (source event id checked via
     *  Redis dedup, then DB unique constraint), triggers async dispatch
     *  on success. */
    void createFromEvent(SourceEventType sourceEventType, NotificationChannel channel, String recipient,
                          String subject, String body, String templateName,
                          String sourceEventId, String correlationId, String traceId,
                          String participantId, String paymentId);

    NotificationResponse getById(UUID id);

    PageResponse<NotificationResponse> search(NotificationSearchCriteria criteria, int page, int size);

    List<DeliveryAttemptResponse> getHistory(UUID notificationId);

    /** Operator-triggered retry of a FAILED/DEAD_LETTERED notification -
     *  resets retry budget and re-dispatches. Admin-only (see
     *  NotificationController). */
    NotificationResponse retry(UUID id, ResendNotificationRequest request);

    /** Creates a brand-new notification with the same content, resent
     *  to the same recipient as a new, separately-tracked send. */
    NotificationResponse resend(UUID id, ResendNotificationRequest request);
}
