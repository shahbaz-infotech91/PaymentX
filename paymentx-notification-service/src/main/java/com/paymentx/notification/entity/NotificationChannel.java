package com.paymentx.notification.entity;

/**
 * WHY PUSH is already a member despite having no PushChannel
 * implementation yet: the explicit requirement is "adding Push
 * Notification later must require almost zero code changes" - having
 * the enum value ready (with NotificationChannelFactory simply not
 * having a registered strategy for it yet) means the day a
 * PushChannel class is added, this enum needs zero changes, only a new
 * @Component implementing NotificationChannel with getChannelType()
 * returning PUSH.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationChannel is a enum in the notification module of PaymentX. It lives in package com.paymentx.notification.entity and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationChannel PaymentX ke notification module ka ek enum hai. Ye com.paymentx.notification.entity package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    WEBHOOK,
    INTERNAL,
    PUSH
}
