package com.paymentx.routing.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Consumed from Kafka - published elsewhere (Validation/Auth Service)
 * when a participant is deactivated. Routing Service reacts by
 * deactivating that participant's routing rules, so payments stop being
 * routed to a participant no longer certified/active on the platform.
 */
@Getter
@Setter
@NoArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ParticipantDeactivatedEvent is a class in the routing module of PaymentX. It lives in package com.paymentx.routing.event and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ParticipantDeactivatedEvent PaymentX ke routing module ka ek class hai. Ye com.paymentx.routing.event package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ParticipantDeactivatedEvent {
    private String participantId;
    private String reason;
}
