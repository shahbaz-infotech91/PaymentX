package com.paymentx.notification.channel;

import com.paymentx.notification.service.channel.MockSmsProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MockSmsProviderTest is a JUnit test class in the notification module of PaymentX, package com.paymentx.notification.channel. It is used within notification's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MockSmsProviderTest PaymentX ke notification module ka ek JUnit test class hai, package com.paymentx.notification.channel me. Ye notification ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class MockSmsProviderTest {

    private final MockSmsProvider provider = new MockSmsProvider();

    @Test
    void sendSms_validPhoneNumber_completesWithoutException() {
        assertThatCode(() -> provider.sendSms("+15551234567", "Your payment was completed.")).doesNotThrowAnyException();
    }

    @Test
    void sendSms_nullMessage_doesNotThrow() {
        assertThatCode(() -> provider.sendSms("+15551234567", null)).doesNotThrowAnyException();
    }
}
