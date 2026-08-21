package com.paymentx.notification.service.channel;

import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SmsChannel is a component in the notification module of PaymentX. It lives in package com.paymentx.notification.service.channel and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SmsChannel PaymentX ke notification module ka ek component hai. Ye com.paymentx.notification.service.channel package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SmsChannel implements NotificationChannelHandler {

    private final SmsProvider smsProvider;

    @Override
    public NotificationChannel getChannelType() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(Notification notification) throws NotificationDeliveryException {
        try {
            smsProvider.sendSms(notification.getRecipient(), notification.getBody());
        } catch (Exception e) {
            throw new NotificationDeliveryException("SMS delivery failed: " + e.getMessage(), e);
        }
    }
}
