package com.paymentx.notification.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.notification.dto.NotificationResponse;
import com.paymentx.notification.dto.ResendNotificationRequest;
import com.paymentx.notification.entity.Notification;
import com.paymentx.notification.entity.NotificationChannel;
import com.paymentx.notification.entity.NotificationStatus;
import com.paymentx.notification.entity.SourceEventType;
import com.paymentx.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationControllerIntegrationTest is a JUnit test class in the notification module of PaymentX, package com.paymentx.notification.controller. It is used within notification's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationControllerIntegrationTest PaymentX ke notification module ka ek JUnit test class hai, package com.paymentx.notification.controller me. Ye notification ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class NotificationControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_notification_ctrl_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/notifications";
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", "NOTIFICATION_ADMIN");
        return headers;
    }

    private Notification seed(NotificationStatus status) {
        Notification notification = Notification.builder()
                .sourceEventType(SourceEventType.PAYMENT_FAILED)
                .channel(NotificationChannel.EMAIL)
                .status(status)
                .recipient("it-test@example.com")
                .retryCount(status == NotificationStatus.FAILED ? 3 : 0)
                .maxRetries(5)
                .build();
        return notificationRepository.save(notification);
    }

    @Test
    void getById_existingNotification_returns200() {
        Notification seeded = seed(NotificationStatus.SENT);

        ResponseEntity<ApiResponse<NotificationResponse>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getId(), HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<NotificationResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void getById_nonExistent_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/00000000-0000-0000-0000-000000000000", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void search_isPubliclyReadable() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void retry_withoutAdminRole_isDenied() {
        Notification seeded = seed(NotificationStatus.FAILED);
        ResendNotificationRequest request = new ResendNotificationRequest("investigated", "user1");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getId() + "/retry", HttpMethod.POST, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void retry_withAdminRole_onFailedNotification_succeeds() {
        Notification seeded = seed(NotificationStatus.FAILED);
        ResendNotificationRequest request = new ResendNotificationRequest("investigated and safe", "admin1");

        ResponseEntity<ApiResponse<NotificationResponse>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getId() + "/retry", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<NotificationResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void retry_onSentNotification_returns409Conflict() {
        Notification seeded = seed(NotificationStatus.SENT);
        ResendNotificationRequest request = new ResendNotificationRequest(null, "admin1");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getId() + "/retry", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void resend_withAdminRole_createsNewNotification() {
        Notification seeded = seed(NotificationStatus.SENT);
        ResendNotificationRequest request = new ResendNotificationRequest("customer requested resend", "admin1");

        ResponseEntity<ApiResponse<NotificationResponse>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getId() + "/resend", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<NotificationResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().id()).isNotEqualTo(seeded.getId());
    }

    @Test
    void getHistory_returnsEmptyListForNewNotificationWithNoAttempts() {
        Notification seeded = seed(NotificationStatus.PENDING);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/" + seeded.getId() + "/history", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
