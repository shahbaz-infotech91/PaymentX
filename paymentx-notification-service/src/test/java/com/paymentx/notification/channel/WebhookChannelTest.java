package com.paymentx.notification.channel;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.paymentx.notification.config.NotificationProperties;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.SourceEventType;
import com.paymentx.notification.service.channel.NotificationDeliveryException;
import com.paymentx.notification.service.channel.WebhookChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * WebhookChannelTest is a JUnit test class in the notification module of PaymentX, package com.paymentx.notification.channel. It is used within notification's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * WebhookChannelTest PaymentX ke notification module ka ek JUnit test class hai, package com.paymentx.notification.channel me. Ye notification ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class WebhookChannelTest {

    private WireMockServer wireMockServer;
    private WebhookChannel webhookChannel;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        NotificationProperties properties = new NotificationProperties();
        properties.getWebhook().setSigningSecret("test-secret");

        var restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofMillis(2000))
                .readTimeout(Duration.ofMillis(2000))
                .build();

        webhookChannel = new WebhookChannel(restTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void send_successfulResponse_completesWithoutExceptionAndSignsPayload() {
        wireMockServer.stubFor(post(urlPathEqualTo("/webhook")).willReturn(aResponse().withStatus(200)));

        Notification notification = Notification.builder()
                .sourceEventType(SourceEventType.PAYMENT_COMPLETED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient("http://localhost:" + wireMockServer.port() + "/webhook")
                .body("{\"paymentId\":\"pay-1\"}")
                .build();

        assertThatCode(() -> webhookChannel.send(notification)).doesNotThrowAnyException();

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/webhook"))
                .withHeader("X-PaymentX-Signature", matching(".+"))
                .withHeader("X-PaymentX-Event-Type", equalTo("PAYMENT_COMPLETED")));
    }

    @Test
    void send_non2xxResponse_throwsDeliveryExceptionWithResponseCode() {
        wireMockServer.stubFor(post(urlPathEqualTo("/webhook")).willReturn(aResponse().withStatus(500)));

        Notification notification = Notification.builder()
                .sourceEventType(SourceEventType.PAYMENT_FAILED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient("http://localhost:" + wireMockServer.port() + "/webhook")
                .body("{}")
                .build();

        assertThatThrownBy(() -> webhookChannel.send(notification))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(ex -> assertThat(((NotificationDeliveryException) ex).getResponseCode()).isEqualTo(500));
    }

    @Test
    void send_unreachableEndpoint_throwsDeliveryException() {
        Notification notification = Notification.builder()
                .sourceEventType(SourceEventType.PAYMENT_FAILED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient("http://localhost:1/unreachable")
                .body("{}")
                .build();

        assertThatThrownBy(() -> webhookChannel.send(notification)).isInstanceOf(NotificationDeliveryException.class);
    }
}
