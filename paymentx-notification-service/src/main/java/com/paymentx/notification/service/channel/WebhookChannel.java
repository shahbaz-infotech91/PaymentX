package com.paymentx.notification.service.channel;

import com.paymentx.notification.config.NotificationProperties;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * WHY HMAC-SHA256 signing (X-PaymentX-Signature header), computed here
 * rather than left to the receiving webhook consumer to trust blindly:
 * "Signature support" is explicit in the requirements - a webhook
 * receiver needs to verify the payload genuinely came from PaymentX and
 * was not tampered with in transit, the same pattern Stripe/Adyen/every
 * mature payments platform's outbound webhooks use.
 *
 * WHY retry/backoff is NOT implemented inside this class: this class's
 * send() either succeeds or throws once - NotificationServiceImpl (via
 * NotificationDispatcher) owns the actual retry-with-backoff SCHEDULING
 * decision, matching the platform's established separation (Payment
 * Service's RetryScheduler doesn't live inside SchemeGateway
 * implementations either). This keeps retry POLICY in one place
 * regardless of channel, rather than duplicated per-channel.
 */
@Component
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * WebhookChannel is a component in the notification module of PaymentX. It lives in package com.paymentx.notification.service.channel and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * WebhookChannel PaymentX ke notification module ka ek component hai. Ye com.paymentx.notification.service.channel package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class WebhookChannel implements NotificationChannelHandler {

    private final RestTemplate restTemplate;
    private final String signingSecret;

    public WebhookChannel(RestTemplate webhookRestTemplate, NotificationProperties notificationProperties) {
        this.restTemplate = webhookRestTemplate;
        this.signingSecret = notificationProperties.getWebhook().getSigningSecret();
    }

    @Override
    public NotificationChannel getChannelType() {
        return NotificationChannel.WEBHOOK;
    }

    @Override
    public void send(Notification notification) throws NotificationDeliveryException {
        String payload = notification.getBody();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-PaymentX-Signature", sign(payload));
        headers.set("X-PaymentX-Event-Type", notification.getSourceEventType().name());

        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        try {
            var response = restTemplate.postForEntity(notification.getRecipient(), request, String.class);
            log.info("Webhook delivered url={} status={}", notification.getRecipient(), response.getStatusCode().value());
        } catch (HttpStatusCodeException e) {
            // RestTemplate's default error handler throws for any non-2xx
            // response rather than returning it normally - this is the
            // ONLY path that actually observes a non-2xx status; the old
            // "if (!status.is2xxSuccessful())" check after postForEntity()
            // was dead code that could never run.
            throw new NotificationDeliveryException("Webhook endpoint returned non-2xx status", e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new NotificationDeliveryException("Webhook delivery failed: " + e.getMessage(), e);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute webhook signature", e);
        }
    }
}
