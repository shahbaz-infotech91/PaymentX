package com.paymentx.validation.service;

import com.paymentx.validation.dto.PaymentValidationRequest;
import com.paymentx.validation.dto.ValidationResponse;
import com.paymentx.validation.entity.ValidationLog;
import com.paymentx.validation.entity.ValidationStatus;
import com.paymentx.validation.event.RejectedPaymentPayload;
import com.paymentx.validation.event.ValidatedPaymentPayload;
import com.paymentx.validation.event.ValidationEventPublisher;
import com.paymentx.validation.exception.DuplicatePaymentException;
import com.paymentx.common.exception.PaymentXException;
import com.paymentx.validation.repository.ValidationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Orchestration order matters and is deliberate:
 *   1. Participant checks first (cheapest, most fundamental - "do these
 *      banks even exist and are they certified for this scheme")
 *   2. Business rule / amount checks (DB read, no side effects yet)
 *   3. Blacklist checks on both debtor and creditor
 *   4. ONLY IF all of the above pass or fail cleanly, attempt the
 *      idempotency claim and persist the audit log + publish the event.
 *
 * WHY run validation logic BEFORE claiming idempotency, rather than
 * claiming first: claiming first would mean writing a placeholder status,
 * then updating it after we know the real outcome - two writes instead of
 * one, and a window where the record exists with a not-yet-final status.
 * Running validation first means we know the final status before we ever
 * touch the idempotency table, so exactly one write happens. The trade-off
 * we accept: a genuine duplicate request re-runs the (cheap, read-only)
 * validation checks before being rejected at the claim step - wasted work,
 * but never wasted CORRECTNESS, since the claim is still what gates
 * whether we log/publish.
 */
@Service
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationService is a service in the validation module of PaymentX. It lives in package com.paymentx.validation.service and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationService PaymentX ke validation module ka ek service hai. Ye com.paymentx.validation.service package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ValidationService {

    private final ParticipantValidationService participantValidationService;
    private final BusinessRuleValidationService businessRuleValidationService;
    private final BlacklistValidationService blacklistValidationService;
    private final IdempotencyService idempotencyService;
    private final ValidationLogRepository validationLogRepository;
    private final ValidationEventPublisher eventPublisher;

    public ValidationResponse validate(PaymentValidationRequest request, String traceIdInput) {
        String traceId = (traceIdInput == null || traceIdInput.isBlank())
                ? UUID.randomUUID().toString()
                : traceIdInput;

        String rejectionReason = null;
        String errorCode = null;

        try {
            participantValidationService.validateParticipant(request.debtorBankId(), request.scheme(), "DEBTOR");
            participantValidationService.validateParticipant(request.creditorBankId(), request.scheme(), "CREDITOR");

            businessRuleValidationService.validateAmount(request.scheme(), request.amount());

            blacklistValidationService.checkNotBlacklisted(request.debtorAccount(), request.debtorBankId(), "DEBTOR");
            blacklistValidationService.checkNotBlacklisted(request.creditorAccount(), request.creditorBankId(), "CREDITOR");

        } catch (PaymentXException ex) {
            rejectionReason = ex.getMessage();
            errorCode = ex.getErrorCode();
        }

        ValidationStatus finalStatus = (rejectionReason == null) ? ValidationStatus.VALIDATED : ValidationStatus.REJECTED;

        // Idempotency claim - this is the atomic gate. If this throws
        // DuplicatePaymentException, we deliberately do NOT write a
        // validation_log row or publish an event for a duplicate, because
        // we already did that the first time this reference came in.
        try {
            idempotencyService.claim(request.paymentReference(), traceId, request.scheme(), finalStatus);
        } catch (DuplicatePaymentException dup) {
            log.warn("Duplicate payment detected paymentReference={} traceId={}", request.paymentReference(), traceId);
            return ValidationResponse.duplicate(request.paymentReference(), traceId);
        }

        persistAuditLog(request, traceId, finalStatus, rejectionReason);

        if (finalStatus == ValidationStatus.VALIDATED) {
            eventPublisher.publishValidated(traceId, ValidatedPaymentPayload.builder()
                    .paymentReference(request.paymentReference())
                    .scheme(request.scheme())
                    .amount(request.amount())
                    .currency(request.currency())
                    .debtorAccount(request.debtorAccount())
                    .debtorParticipantId(request.debtorBankId())
                    .creditorAccount(request.creditorAccount())
                    .creditorParticipantId(request.creditorBankId())
                    .build());

            return ValidationResponse.validated(request.paymentReference(), traceId);
        } else {
            eventPublisher.publishRejected(traceId, RejectedPaymentPayload.builder()
                    .paymentReference(request.paymentReference())
                    .scheme(request.scheme())
                    .rejectionReason(rejectionReason)
                    .errorCode(errorCode)
                    .build());

            return ValidationResponse.rejected(request.paymentReference(), traceId, rejectionReason);
        }
    }

    private void persistAuditLog(PaymentValidationRequest request, String traceId,
                                  ValidationStatus status, String rejectionReason) {
        ValidationLog logEntry = ValidationLog.builder()
                .paymentReference(request.paymentReference())
                .traceId(traceId)
                .scheme(request.scheme())
                .amount(request.amount())
                .currency(request.currency())
                .debtorAccount(request.debtorAccount())
                .debtorBankId(request.debtorBankId())
                .creditorAccount(request.creditorAccount())
                .creditorBankId(request.creditorBankId())
                .validationStatus(status)
                .rejectionReason(rejectionReason)
                .validatedAt(OffsetDateTime.now())
                .build();

        validationLogRepository.save(logEntry);
    }
}
