package com.paymentx.notification.service.channel;

import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WHY send() has no external I/O at all: an "internal notification" IS
 * the Notification row itself (visible via GET /notifications) - there
 * is no external system to deliver TO. This channel's entire job is
 * confirming the row exists and is queryable, which
 * NotificationServiceImpl already guarantees by persisting it before
 * dispatch runs. This is a genuinely complete implementation, not a
 * stub - "delivery" for this channel IS persistence, which has already
 * happened by the time this method runs.
 */
@Component
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * InternalChannel is a component in the notification module of PaymentX. It lives in package com.paymentx.notification.service.channel and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * InternalChannel PaymentX ke notification module ka ek component hai. Ye com.paymentx.notification.service.channel package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class InternalChannel implements NotificationChannelHandler {

    @Override
    public NotificationChannel getChannelType() {
        return NotificationChannel.INTERNAL;
    }

    @Override
    public void send(Notification notification) {
        log.info("Internal notification available id={} recipient={}", notification.getId(), notification.getRecipient());
    }
}
