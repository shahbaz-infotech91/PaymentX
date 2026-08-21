package com.paymentx.validation.service;

import com.paymentx.validation.entity.Scheme;
import com.paymentx.validation.entity.ValidationStatus;
import com.paymentx.validation.exception.DuplicatePaymentException;
import com.paymentx.validation.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * IdempotencyServiceTest is a JUnit test class in the validation module of PaymentX, package com.paymentx.validation.service. It is used within validation's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * IdempotencyServiceTest PaymentX ke validation module ka ek JUnit test class hai, package com.paymentx.validation.service me. Ye validation ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void claim_whenReferenceIsNew_savesSuccessfully() {
        assertThatCode(() ->
                idempotencyService.claim("PAY-REF-NEW", "trace-1", Scheme.REAL_TIME_PAYMENT, ValidationStatus.VALIDATED)
        ).doesNotThrowAnyException();

        verify(idempotencyRecordRepository).saveAndFlush(any());
    }

    @Test
    void claim_whenReferenceAlreadyExists_translatesToDuplicatePaymentException() {
        // Simulates the UNIQUE constraint firing on payment_reference -
        // this is what a real concurrent duplicate insert looks like from
        // Spring Data's perspective.
        when(idempotencyRecordRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() ->
                idempotencyService.claim("PAY-REF-DUP", "trace-2", Scheme.REAL_TIME_PAYMENT, ValidationStatus.VALIDATED)
        )
                .isInstanceOf(DuplicatePaymentException.class)
                .hasMessageContaining("PAY-REF-DUP");
    }
}
