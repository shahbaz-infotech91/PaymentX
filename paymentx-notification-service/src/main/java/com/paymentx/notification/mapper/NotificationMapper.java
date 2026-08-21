package com.paymentx.notification.mapper;

import com.paymentx.notification.dto.DeliveryAttemptResponse;
import com.paymentx.notification.dto.NotificationResponse;
import com.paymentx.notification.entity.DeliveryAttempt;
import com.paymentx.notification.entity.Notification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationMapper is a interface in the notification module of PaymentX. It lives in package com.paymentx.notification.mapper and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationMapper PaymentX ke notification module ka ek interface hai. Ye com.paymentx.notification.mapper package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface NotificationMapper {

    NotificationResponse toResponse(Notification entity);

    DeliveryAttemptResponse toAttemptResponse(DeliveryAttempt entity);

    List<DeliveryAttemptResponse> toAttemptResponses(List<DeliveryAttempt> entities);
}
