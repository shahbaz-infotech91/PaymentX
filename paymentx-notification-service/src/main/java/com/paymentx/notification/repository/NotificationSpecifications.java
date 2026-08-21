package com.paymentx.notification.repository;

import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.NotificationStatus;
import com.paymentx.notification.entity.SourceEventType;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationSpecifications is a class in the notification module of PaymentX. It lives in package com.paymentx.notification.repository and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationSpecifications PaymentX ke notification module ka ek class hai. Ye com.paymentx.notification.repository package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {}

    public static Specification<Notification> hasStatus(NotificationStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Notification> hasChannel(NotificationChannel channel) {
        return (root, query, cb) -> channel == null ? cb.conjunction() : cb.equal(root.get("channel"), channel);
    }

    public static Specification<Notification> hasSourceEventType(SourceEventType eventType) {
        return (root, query, cb) -> eventType == null ? cb.conjunction() : cb.equal(root.get("sourceEventType"), eventType);
    }

    public static Specification<Notification> hasRecipient(String recipient) {
        return (root, query, cb) -> recipient == null || recipient.isBlank()
                ? cb.conjunction() : cb.equal(root.get("recipient"), recipient);
    }

    public static Specification<Notification> hasParticipantId(String participantId) {
        return (root, query, cb) -> participantId == null || participantId.isBlank()
                ? cb.conjunction() : cb.equal(root.get("participantId"), participantId);
    }

    public static Specification<Notification> createdAfter(OffsetDateTime from) {
        return (root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Notification> createdBefore(OffsetDateTime to) {
        return (root, query, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
