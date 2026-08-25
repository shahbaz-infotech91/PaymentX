package com.paymentx.payment.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.payment.dto.CancellationRequest;
import com.paymentx.payment.dto.PaymentHistoryResponse;
import com.paymentx.payment.dto.PaymentResponse;
import com.paymentx.payment.dto.PaymentRetryRequest;
import com.paymentx.payment.dto.PaymentStatusResponse;
import com.paymentx.payment.entity.Money;
import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentChannel;
import com.paymentx.payment.entity.PaymentScheme;
import com.paymentx.payment.entity.PaymentStatus;
import com.paymentx.payment.entity.PaymentStatusHistory;
import com.paymentx.payment.entity.PaymentType;
import com.paymentx.payment.repository.PaymentRepository;
import com.paymentx.payment.repository.PaymentRetryRepository;
import com.paymentx.payment.repository.PaymentStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WHY Testcontainers Postgres (real DB, not H2) - same rationale
 * established for Validation Service's integration tests: Liquibase
 * changesets and the Specification-based dynamic queries built for this
 * controller layer need to run against the actual database engine they
 * target in production.
 *
 * WHY no Kafka Testcontainer here: nothing in the controller/service
 * layer built for this task calls a Kafka producer directly - cancel()
 * writes to payment_outbox via OutboxEventWriter (a DB write only); the
 * actual Kafka publish happens later via OutboxProcessor, which isn't
 * exercised by these tests. Spring Kafka's producer/consumer beans
 * connect lazily, so their configured (unreachable in this test) broker
 * address does not block application context startup.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentControllerIntegrationTest is a JUnit test class in the payment module of PaymentX, package com.paymentx.payment.controller. It is used within payment's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentControllerIntegrationTest PaymentX ke payment module ka ek JUnit test class hai, package com.paymentx.payment.controller me. Ye payment ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class PaymentControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_payment_test")
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
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentRetryRepository retryRepository;

    @Autowired
    private PaymentStatusHistoryRepository statusHistoryRepository;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/payments";
    }

    private Payment seedPayment(PaymentStatus status) {
        Payment payment = Payment.builder()
                .paymentReference("IT-PAY-" + UUID.randomUUID())
                .idempotencyKey(UUID.randomUUID().toString())
                .traceId(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .scheme(PaymentScheme.INSTANT_PAYMENT)
                .paymentType(PaymentType.DEBIT)
                .channel(PaymentChannel.API)
                .amount(Money.of(new BigDecimal("250.00"), "USD"))
                .debtorAccount("ACC-DEBTOR-01")
                .debtorParticipantId("BANK001")
                .creditorAccount("ACC-CREDITOR-01")
                .creditorParticipantId("BANK002")
                .status(status)
                .build();
        return paymentRepository.save(payment);
    }

    @Test
    void getByReference_existingPayment_returns200WithFullDetails() {
        Payment seeded = seedPayment(PaymentStatus.SETTLED);

        ResponseEntity<ApiResponse<PaymentResponse>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getPaymentReference(),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().paymentReference()).isEqualTo(seeded.getPaymentReference());
        assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.SETTLED);
    }

    @Test
    void getByReference_nonExistentPayment_returns404() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/DOES-NOT-EXIST",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void getStatus_returnsLightweightStatusPayload() {
        Payment seeded = seedPayment(PaymentStatus.PROCESSING);

        ResponseEntity<ApiResponse<PaymentStatusResponse>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getPaymentReference() + "/status",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<PaymentStatusResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void getHistory_realTransitions_returnsOrderedTimelineWithCurrentSnapshot() {
        Payment seeded = seedPayment(PaymentStatus.FAILED);
        statusHistoryRepository.save(PaymentStatusHistory.builder()
                .paymentId(seeded.getId())
                .fromStatus(PaymentStatus.RECEIVED)
                .toStatus(PaymentStatus.PROCESSING)
                .reason("validation completed")
                .transitionedAt(OffsetDateTime.now().minusMinutes(2))
                .build());
        statusHistoryRepository.save(PaymentStatusHistory.builder()
                .paymentId(seeded.getId())
                .fromStatus(PaymentStatus.PROCESSING)
                .toStatus(PaymentStatus.FAILED)
                .reason("downstream timeout")
                .transitionedAt(OffsetDateTime.now().minusMinutes(1))
                .build());

        ResponseEntity<ApiResponse<PaymentHistoryResponse>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getPaymentReference() + "/history",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<PaymentHistoryResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PaymentHistoryResponse body = response.getBody().data();
        assertThat(body.payment().paymentReference()).isEqualTo(seeded.getPaymentReference());
        assertThat(body.history()).hasSize(2);
        assertThat(body.history().get(0).toStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(body.history().get(0).reason()).isEqualTo("validation completed");
        assertThat(body.history().get(1).toStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(body.history().get(1).reason()).isEqualTo("downstream timeout");
        // Ordered oldest-first (transitionedAt ASC) - a real timeline, not insertion order.
        assertThat(body.history().get(0).transitionedAt()).isBefore(body.history().get(1).transitionedAt());
    }

    @Test
    void getHistory_paymentWithNoRecordedTransitions_returnsEmptyListNotAnError() {
        Payment seeded = seedPayment(PaymentStatus.RECEIVED);

        ResponseEntity<ApiResponse<PaymentHistoryResponse>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getPaymentReference() + "/history",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<PaymentHistoryResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().history()).isEmpty();
    }

    @Test
    void getHistory_nonExistentPayment_returns404() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/DOES-NOT-EXIST/history",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error().errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void list_returnsPaginatedResults() {
        seedPayment(PaymentStatus.SETTLED);
        seedPayment(PaymentStatus.SETTLED);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "?page=0&size=10", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"content\"");
    }

    @Test
    void search_byStatusAndParticipant_returnsMatchingResults() {
        Payment seeded = seedPayment(PaymentStatus.FAILED);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/search?status=FAILED&participantId=" + seeded.getDebtorParticipantId(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(seeded.getPaymentReference());
    }

    @Test
    void retry_onFailedPayment_succeedsAndSeedsRetryRecord() {
        Payment seeded = seedPayment(PaymentStatus.FAILED);
        PaymentRetryRequest request = new PaymentRetryRequest("Operator investigated and confirmed retry is safe", "ops-user-1");

        ResponseEntity<ApiResponse<PaymentResponse>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getPaymentReference() + "/retry",
                HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.RETRYING);
        assertThat(retryRepository.findByPaymentIdOrderByCurrentRetryAsc(seeded.getId())).isNotEmpty();
    }

    @Test
    void retry_onSettledPayment_returns409Conflict() {
        Payment seeded = seedPayment(PaymentStatus.SETTLED);
        PaymentRetryRequest request = new PaymentRetryRequest(null, "ops-user-1");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getPaymentReference() + "/retry",
                HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void retry_missingRequestedBy_returns400ValidationError() {
        Payment seeded = seedPayment(PaymentStatus.FAILED);
        PaymentRetryRequest request = new PaymentRetryRequest("some reason", "");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getPaymentReference() + "/retry",
                HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancel_onReceivedPayment_succeeds() {
        Payment seeded = seedPayment(PaymentStatus.RECEIVED);
        CancellationRequest request = new CancellationRequest("Customer requested cancellation before processing", "ops-user-1");

        ResponseEntity<ApiResponse<PaymentResponse>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getPaymentReference() + "/cancel",
                HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void cancel_onSettledPayment_returns409Conflict_fundsAlreadyMoved() {
        Payment seeded = seedPayment(PaymentStatus.SETTLED);
        CancellationRequest request = new CancellationRequest("Too late attempt", "ops-user-1");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + seeded.getPaymentReference() + "/cancel",
                HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
