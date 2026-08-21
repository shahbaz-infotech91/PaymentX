package com.paymentx.notification.channel;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.paymentx.notification.config.NotificationProperties;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.SourceEventType;
import com.paymentx.notification.service.channel.EmailChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * EmailChannelTest is a JUnit test class in the notification module of PaymentX, package com.paymentx.notification.channel. It is used within notification's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * EmailChannelTest PaymentX ke notification module ka ek JUnit test class hai, package com.paymentx.notification.channel me. Ye notification ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class EmailChannelTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    private EmailChannel emailChannel;

    @BeforeEach
    void setUp() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(greenMail.getSmtp().getPort());

        NotificationProperties properties = new NotificationProperties();
        properties.getEmail().setFrom("noreply@paymentx.local");

        emailChannel = new EmailChannel(mailSender, properties);
    }

    @Test
    void send_validNotification_deliversToGreenMailServer() throws Exception {
        Notification notification = Notification.builder()
                .sourceEventType(SourceEventType.PAYMENT_COMPLETED)
                .channel(NotificationChannel.EMAIL)
                .recipient("customer@example.com")
                .subject("Payment Completed")
                .body("<p>Your payment was completed successfully.</p>")
                .build();

        assertThatCode(() -> emailChannel.send(notification)).doesNotThrowAnyException();

        var receivedMessages = greenMail.getReceivedMessages();
        assertThat(receivedMessages).hasSizeGreaterThanOrEqualTo(1);
        var lastMessage = receivedMessages[receivedMessages.length - 1];
        assertThat(lastMessage.getSubject()).isEqualTo("Payment Completed");
        assertThat(lastMessage.getAllRecipients()[0].toString()).isEqualTo("customer@example.com");
    }

    @Test
    void send_noSubject_usesDefaultSubject() throws Exception {
        Notification notification = Notification.builder()
                .sourceEventType(SourceEventType.SECURITY_EVENT)
                .channel(NotificationChannel.EMAIL)
                .recipient("customer@example.com")
                .subject(null)
                .body("<p>Security alert.</p>")
                .build();

        emailChannel.send(notification);

        var receivedMessages = greenMail.getReceivedMessages();
        var lastMessage = receivedMessages[receivedMessages.length - 1];
        assertThat(lastMessage.getSubject()).isEqualTo("PaymentX Notification");
    }
}
