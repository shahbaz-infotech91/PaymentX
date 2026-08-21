package com.paymentx.notification.repository;

import com.paymentx.notification.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * DeliveryAttemptRepository is a interface in the notification module of PaymentX. It lives in package com.paymentx.notification.repository and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * DeliveryAttemptRepository PaymentX ke notification module ka ek interface hai. Ye com.paymentx.notification.repository package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByNotificationIdOrderByAttemptNumberAsc(UUID notificationId);
}
