package com.paymentx.validation.service;

import com.paymentx.validation.entity.IdempotencyRecord;
import com.paymentx.validation.entity.Scheme;
import com.paymentx.validation.entity.ValidationStatus;
import com.paymentx.validation.exception.DuplicatePaymentException;
import com.paymentx.validation.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * See V1_0_5 changeset comment for the full race-condition explanation.
 * The short version: the UNIQUE constraint on payment_reference is the
 * actual duplicate-detection mechanism; this class is just a thin,
 * correctly-scoped wrapper around it.
 *
 * WHY Propagation.REQUIRES_NEW here specifically:
 * The caller (ValidationService) runs inside its own @Transactional method
 * that ALSO writes a validation_log row and (conceptually) publishes a
 * Kafka event. If the idempotency claim ran in that SAME transaction and
 * something later in the flow threw an unexpected exception, Spring would
 * roll back the ENTIRE transaction - including the idempotency claim we
 * just made. That would un-claim the reference, and a genuine retry of the
 * same payment would be allowed through again, defeating the entire point.
 * REQUIRES_NEW forces the claim to commit in its own transaction,
 * independent of whatever happens next.
 */
@Service
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * IdempotencyService is a service in the validation module of PaymentX. It lives in package com.paymentx.validation.service and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * IdempotencyService PaymentX ke validation module ka ek service hai. Ye com.paymentx.validation.service package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(String paymentReference, String traceId, Scheme scheme, ValidationStatus resultStatus) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .paymentReference(paymentReference)
                .traceId(traceId)
                .scheme(scheme)
                .resultStatus(resultStatus)
                .firstSeenAt(OffsetDateTime.now())
                .build();

        try {
            idempotencyRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // The UNIQUE constraint fired - someone already claimed this
            // reference (possibly a concurrent request, possibly a true
            // retry of an earlier call). Either way, we do not proceed.
            throw new DuplicatePaymentException(paymentReference);
        }
    }
}
