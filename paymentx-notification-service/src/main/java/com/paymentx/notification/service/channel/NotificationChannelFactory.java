package com.paymentx.notification.service.channel;

import com.paymentx.notification.entity.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WHY List<NotificationChannelHandler> constructor injection, not a
 * hand-maintained switch/if-chain: Spring collects every
 * @Component-annotated NotificationChannelHandler implementation
 * automatically. Adding PushChannel later means writing that one class
 * with getChannelType() returning PUSH - this factory needs ZERO code
 * changes, satisfying "adding Push Notification later must require
 * almost zero code changes" precisely.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationChannelFactory is a component in the notification module of PaymentX. It lives in package com.paymentx.notification.service.channel and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationChannelFactory PaymentX ke notification module ka ek component hai. Ye com.paymentx.notification.service.channel package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class NotificationChannelFactory {

    private final Map<NotificationChannel, NotificationChannelHandler> handlers;

    public NotificationChannelFactory(List<NotificationChannelHandler> channelHandlers) {
        this.handlers = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannelHandler handler : channelHandlers) {
            handlers.put(handler.getChannelType(), handler);
        }
    }

    public Optional<NotificationChannelHandler> getHandler(NotificationChannel channel) {
        return Optional.ofNullable(handlers.get(channel));
    }
}
