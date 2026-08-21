package com.paymentx.notification.service.channel;

import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;

/**
 * WHY this interface, not a switch-statement in NotificationDispatcher:
 * the explicit requirement is "adding Push Notification later must
 * require almost zero code changes" - a switch would need a new case
 * added at the dispatcher every time a channel is added. With this
 * Strategy pattern, NotificationChannelFactory autodetects every Spring
 * bean implementing this interface (constructor-injected as
 * List<NotificationChannelHandler>) - adding PushChannel later means
 * writing ONE new @Component class and nothing else changes.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationChannelHandler is a interface in the notification module of PaymentX. It lives in package com.paymentx.notification.service.channel and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationChannelHandler PaymentX ke notification module ka ek interface hai. Ye com.paymentx.notification.service.channel package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface NotificationChannelHandler {

    NotificationChannel getChannelType();

    /** Attempts delivery. Returns normally on success; throws on
     *  failure - NotificationDispatcher/NotificationServiceImpl own
     *  translating a thrown exception into a DeliveryAttempt row and
     *  retry-scheduling decision, keeping that policy out of every
     *  individual channel implementation. */
    void send(Notification notification) throws NotificationDeliveryException;
}
