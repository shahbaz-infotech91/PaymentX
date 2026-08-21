package com.paymentx.notification.service.channel;

import com.paymentx.notification.config.NotificationProperties;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * WHY MimeMessageHelper (HTML-capable) rather than SimpleMailMessage:
 * the explicit requirement is "HTML Templates" + "Plain Text" +
 * "Attachments ready" - SimpleMailMessage cannot send HTML or
 * attachments at all. MimeMessageHelper's multipart=true constructor
 * argument enables attachment support even though this implementation
 * doesn't attach a file today (Notification.body carries no attachment
 * reference yet) - the plumbing is ready without dead/unreachable code,
 * since addAttachment() is simply never called, not present-but-disabled.
 *
 * WHY body is expected to already be fully-rendered HTML (produced by
 * EmailTemplateService before this class ever sees it), not raw
 * template+variables: keeps this class's only responsibility "send
 * whatever HTML/text was given," matching NotificationChannelHandler's
 * single send() contract - template rendering is NotificationServiceImpl's
 * concern (see its use of EmailTemplateService), not this channel's.
 */
@Component
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * EmailChannel is a component in the notification module of PaymentX. It lives in package com.paymentx.notification.service.channel and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * EmailChannel PaymentX ke notification module ka ek component hai. Ye com.paymentx.notification.service.channel package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class EmailChannel implements NotificationChannelHandler {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public EmailChannel(JavaMailSender javaMailSender, NotificationProperties notificationProperties) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = notificationProperties.getEmail().getFrom();
    }

    @Override
    public NotificationChannel getChannelType() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification) throws NotificationDeliveryException {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress);
            helper.setTo(notification.getRecipient());
            helper.setSubject(notification.getSubject() != null ? notification.getSubject() : "PaymentX Notification");
            helper.setText(notification.getBody(), true);

            javaMailSender.send(mimeMessage);
            log.info("Email sent recipient={} subject={}", maskEmail(notification.getRecipient()), notification.getSubject());
        } catch (Exception e) {
            throw new NotificationDeliveryException("Email delivery failed: " + e.getMessage(), e);
        }
    }

    /** WHY email addresses are masked in logs, never logged in full: the
     *  explicit "mask email addresses" logging requirement - an email
     *  address is PII, and a log aggregator is a much wider blast radius
     *  than the database this value is already stored in (with
     *  appropriate access controls). Shows enough to correlate/debug
     *  (first char + domain) without exposing the full address. */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String maskedLocal = localPart.isEmpty() ? "*" : localPart.charAt(0) + "***";
        return maskedLocal + domain;
    }
}
