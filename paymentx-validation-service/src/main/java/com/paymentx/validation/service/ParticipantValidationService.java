package com.paymentx.validation.service;

import com.paymentx.validation.entity.Participant;
import com.paymentx.validation.entity.ParticipantStatus;
import com.paymentx.validation.entity.Scheme;
import com.paymentx.validation.exception.BusinessRuleViolationException;
import com.paymentx.validation.repository.ParticipantRepository;
import com.paymentx.validation.repository.ParticipantSchemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Checks WHO is sending/receiving the payment, before we ever look at
 * amounts or blacklists. Cheapest, most fundamental check first:
 * "do these two banks even exist in our network, are they active, and
 * are they certified for the scheme being used" - if any of that fails,
 * there's no point running amount-limit or blacklist logic at all.
 */
@Service
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ParticipantValidationService is a service in the validation module of PaymentX. It lives in package com.paymentx.validation.service and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ParticipantValidationService PaymentX ke validation module ka ek service hai. Ye com.paymentx.validation.service package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ParticipantValidationService {

    private final ParticipantRepository participantRepository;
    private final ParticipantSchemeRepository participantSchemeRepository;

    public void validateParticipant(String bankId, Scheme scheme, String role) {
        Participant participant = participantRepository.findByBankId(bankId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "PARTICIPANT_UNKNOWN_" + role,
                        role + " bank '" + bankId + "' is not a registered PaymentX participant"));

        if (participant.getStatus() != ParticipantStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "PARTICIPANT_INACTIVE_" + role,
                    role + " bank '" + bankId + "' is not ACTIVE (status=" + participant.getStatus() + ")");
        }

        participantSchemeRepository
                .findByParticipantIdAndSchemeAndActiveTrue(participant.getId(), scheme)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "SCHEME_NOT_CERTIFIED_" + role,
                        role + " bank '" + bankId + "' is not certified for scheme " + scheme));
    }
}
