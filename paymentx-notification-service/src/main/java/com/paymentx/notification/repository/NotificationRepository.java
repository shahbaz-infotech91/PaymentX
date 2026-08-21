package com.paymentx.notification.repository;

import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationRepository is a interface in the notification module of PaymentX. It lives in package com.paymentx.notification.repository and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationRepository PaymentX ke notification module ka ek interface hai. Ye com.paymentx.notification.repository package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    boolean existsBySourceEventId(String sourceEventId);

    /** Picked up by the retry scheduler - notifications past their
     *  backoff window that are still under budget for another attempt. */
    List<Notification> findByStatusAndNextRetryAtLessThanEqual(NotificationStatus status, OffsetDateTime now);
}
