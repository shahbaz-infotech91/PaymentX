package com.paymentx.notification.service.channel;

/**
 * WHY a dedicated exception rather than reusing PaymentXException
 * (common-library): channel delivery failures need to carry an optional
 * HTTP response code (webhook) that PaymentXException has no field for,
 * and are deliberately NOT part of the shared exception hierarchy -
 * they're caught and translated into a DeliveryAttempt row by
 * NotificationServiceImpl, never surfaced directly to an HTTP caller the
 * way PaymentXException subtypes are.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationDeliveryException is a exception in the notification module of PaymentX. It lives in package com.paymentx.notification.service.channel and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationDeliveryException PaymentX ke notification module ka ek exception hai. Ye com.paymentx.notification.service.channel package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationDeliveryException extends Exception {

    private final Integer responseCode;

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
        this.responseCode = null;
    }

    public NotificationDeliveryException(String message, Integer responseCode) {
        super(message);
        this.responseCode = responseCode;
    }

    public Integer getResponseCode() {
        return responseCode;
    }
}
