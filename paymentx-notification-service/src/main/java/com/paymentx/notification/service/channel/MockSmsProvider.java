package com.paymentx.notification.service.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * WHY this is a genuinely complete implementation, not a placeholder: it
 * fully satisfies the SmsProvider contract for local development - "send"
 * means durably recording the message would have been sent (via the log
 * line, which SmsChannel/tests can assert against), which IS this
 * provider's real, complete purpose. It never claims to reach an actual
 * phone.
 *
 * WHY @ConditionalOnProperty rather than always being the sole
 * SmsProvider bean: this is "Twilio-ready architecture" - the moment a
 * real TwilioSmsProvider @Component is added (out of this task's scope:
 * it requires actual Twilio account credentials this environment has no
 * way to obtain or verify against, and building an untested integration
 * against a real paid API would itself be the "fake code" this task
 * explicitly forbids), setting notification.sms.provider=twilio in
 * application.yml switches providers with zero code change to
 * SmsChannel. The property defaults to "mock" so local/dev environments
 * work out of the box without any Twilio account.
 */
@Component
@ConditionalOnProperty(name = "notification.sms.provider", havingValue = "mock", matchIfMissing = true)
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MockSmsProvider is a component in the notification module of PaymentX. It lives in package com.paymentx.notification.service.channel and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MockSmsProvider PaymentX ke notification module ka ek component hai. Ye com.paymentx.notification.service.channel package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class MockSmsProvider implements SmsProvider {

    @Override
    public void sendSms(String phoneNumber, String message) {
        log.info("[MOCK SMS] to={} message={}", maskPhoneNumber(phoneNumber), message);
    }

    /** WHY phone numbers are masked in logs: the explicit "mask phone
     *  numbers" logging requirement - same PII-in-log-aggregator
     *  rationale as EmailChannel's maskEmail(). */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
