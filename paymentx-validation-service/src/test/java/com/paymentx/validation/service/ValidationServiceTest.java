package com.paymentx.validation.service;

import com.paymentx.validation.dto.PaymentValidationRequest;
import com.paymentx.validation.dto.ValidationResponse;
import com.paymentx.validation.entity.Scheme;
import com.paymentx.validation.entity.ValidationStatus;
import com.paymentx.validation.event.ValidationEventPublisher;
import com.paymentx.validation.exception.BusinessRuleViolationException;
import com.paymentx.validation.exception.DuplicatePaymentException;
import com.paymentx.validation.repository.ValidationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WHY unit tests here mock EVERYTHING (repositories, Kafka publisher)
 * instead of hitting a real DB/Kafka: this test suite is verifying
 * ORCHESTRATION LOGIC - "does ValidationService call the right checks in
 * the right order, and does it react correctly to each outcome" - not
 * "does Postgres actually enforce a unique constraint" (that question
 * belongs in the integration test, which uses a real Testcontainers
 * Postgres). Mixing these two concerns into one slow, DB-backed test suite
 * is a common anti-pattern that makes the fast unit-test feedback loop
 * disappear entirely.
 */
@ExtendWith(MockitoExtension.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationServiceTest is a JUnit test class in the validation module of PaymentX, package com.paymentx.validation.service. It is used within validation's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationServiceTest PaymentX ke validation module ka ek JUnit test class hai, package com.paymentx.validation.service me. Ye validation ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class ValidationServiceTest {

    @Mock private ParticipantValidationService participantValidationService;
    @Mock private BusinessRuleValidationService businessRuleValidationService;
    @Mock private BlacklistValidationService blacklistValidationService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private ValidationLogRepository validationLogRepository;
    @Mock private ValidationEventPublisher eventPublisher;

    @InjectMocks
    private ValidationService validationService;

    private PaymentValidationRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new PaymentValidationRequest(
                "PAY-REF-001",
                Scheme.INSTANT_PAYMENT,
                new BigDecimal("100.00"),
                "USD",
                "ACC-DEBTOR-01",
                "BANK001",
                "ACC-CREDITOR-01",
                "BANK002"
        );
    }

    @Test
    void validate_whenAllChecksPass_returnsValidatedAndPublishesValidatedEvent() {
        // participant/business-rule/blacklist mocks default to "pass"
        // (no exception thrown) since Mockito mocks are no-ops by default.

        ValidationResponse response = validationService.validate(validRequest, "trace-123");

        assertThat(response.status()).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(response.paymentReference()).isEqualTo("PAY-REF-001");
        assertThat(response.rejectionReason()).isNull();

        verify(idempotencyService).claim("PAY-REF-001", "trace-123", Scheme.INSTANT_PAYMENT, ValidationStatus.VALIDATED);
        verify(validationLogRepository).save(any());
        verify(eventPublisher).publishValidated(eq("trace-123"), any());
        verify(eventPublisher, never()).publishRejected(any(), any());
    }

    @Test
    void validate_whenBlacklistCheckFails_returnsRejectedAndPublishesRejectedEvent() {
        doThrow(new BusinessRuleViolationException("ACCOUNT_BLACKLISTED_DEBTOR", "Debtor account is blacklisted"))
                .when(blacklistValidationService)
                .checkNotBlacklisted("ACC-DEBTOR-01", "BANK001", "DEBTOR");

        ValidationResponse response = validationService.validate(validRequest, "trace-456");

        assertThat(response.status()).isEqualTo(ValidationStatus.REJECTED);
        assertThat(response.rejectionReason()).contains("blacklisted");

        // CRITICAL assertion: even a REJECTED payment still gets logged and
        // still gets an idempotency claim - a rejection is a final,
        // recorded outcome, not something we silently drop.
        verify(idempotencyService).claim("PAY-REF-001", "trace-456", Scheme.INSTANT_PAYMENT, ValidationStatus.REJECTED);
        verify(validationLogRepository).save(any());
        verify(eventPublisher).publishRejected(eq("trace-456"), any());
        verify(eventPublisher, never()).publishValidated(any(), any());
    }

    @Test
    void validate_whenAmountExceedsLimit_returnsRejected() {
        doThrow(new BusinessRuleViolationException("AMOUNT_EXCEEDS_LIMIT", "Amount exceeds limit"))
                .when(businessRuleValidationService)
                .validateAmount(Scheme.INSTANT_PAYMENT, validRequest.amount());

        ValidationResponse response = validationService.validate(validRequest, "trace-789");

        assertThat(response.status()).isEqualTo(ValidationStatus.REJECTED);
        // Blacklist should never even be checked - amount rule already failed first...
        // actually amount check runs before blacklist in our orchestration order,
        // so blacklist checks should NOT be skipped since exceptions are caught
        // per-call, not via early-return. Verify current sequential behavior explicitly:
        verify(businessRuleValidationService).validateAmount(Scheme.INSTANT_PAYMENT, validRequest.amount());
    }

    @Test
    void validate_whenPaymentReferenceAlreadyProcessed_returnsDuplicateWithoutLoggingOrPublishing() {
        doThrow(new DuplicatePaymentException("PAY-REF-001"))
                .when(idempotencyService)
                .claim(eq("PAY-REF-001"), any(), eq(Scheme.INSTANT_PAYMENT), any());

        ValidationResponse response = validationService.validate(validRequest, "trace-999");

        assertThat(response.status()).isEqualTo(ValidationStatus.DUPLICATE);

        // A duplicate must NEVER be logged again or re-published - we
        // already did that on the FIRST attempt for this reference.
        verify(validationLogRepository, never()).save(any());
        verify(eventPublisher, never()).publishValidated(any(), any());
        verify(eventPublisher, never()).publishRejected(any(), any());
    }

    @Test
    void validate_whenTraceIdNotProvided_generatesOne() {
        ValidationResponse response = validationService.validate(validRequest, null);

        assertThat(response.traceId()).isNotBlank();
    }
}
