package com.paymentx.notification.service.channel;

/**
 * WHY this abstraction exists separately from NotificationChannelHandler:
 * "Twilio-ready architecture" + "Mock provider for local development"
 * are TWO different SMS PROVIDERS behind the SAME channel concept -
 * SmsChannel (the NotificationChannelHandler) owns retry/masking/status
 * bookkeeping identically regardless of provider; SmsProvider owns only
 * "how do I actually transmit this text message." Swapping
 * MockSmsProvider for a real TwilioSmsProvider bean (via
 * @ConditionalOnProperty, see MockSmsProvider's javadoc) requires zero
 * changes to SmsChannel itself.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SmsProvider is a interface in the notification module of PaymentX. It lives in package com.paymentx.notification.service.channel and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SmsProvider PaymentX ke notification module ka ek interface hai. Ye com.paymentx.notification.service.channel package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface SmsProvider {
    void sendSms(String phoneNumber, String message) throws Exception;
}
