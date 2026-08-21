package com.paymentx.validation.controller;

import com.paymentx.validation.dto.PaymentValidationRequest;
import com.paymentx.validation.dto.ValidationResponse;
import com.paymentx.validation.entity.Scheme;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WHY Testcontainers instead of H2 in-memory DB for this test:
 * Our Liquibase changesets use Postgres-specific types (TIMESTAMP WITH
 * TIME ZONE, BIGSERIAL) and our idempotency mechanism depends on Postgres's
 * actual UNIQUE constraint enforcement semantics under concurrent writes.
 * H2's "Postgres compatibility mode" does NOT perfectly replicate this -
 * tests would pass against H2 and then the exact race condition we built
 * IdempotencyService to guard against could behave differently in real
 * production Postgres. Testcontainers spins up the real thing.
 *
 * WHY @SpringBootTest(webEnvironment = RANDOM_PORT) + TestRestTemplate
 * instead of MockMvc: this test intentionally goes through the full HTTP
 * stack (real servlet container, real JSON serialization, real Liquibase
 * migration on startup) to catch integration-layer bugs MockMvc's
 * in-process dispatch can miss (e.g. Jackson serialization quirks on the
 * real wire format).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationControllerIntegrationTest is a JUnit test class in the validation module of PaymentX, package com.paymentx.validation.controller. It is used within validation's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationControllerIntegrationTest PaymentX ke validation module ka ek JUnit test class hai, package com.paymentx.validation.controller me. Ye validation ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class ValidationControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_validation_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // Liquibase changelog path is already set in application.yml and
        // applies regardless of datasource target - Liquibase runs its
        // migrations against WHATEVER datasource is configured, including
        // this ephemeral Testcontainers instance, on every test run. This
        // is exactly what we want: the test proves the migrations
        // themselves work, not just that application code compiles.
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/validations";
    }

    @Test
    void validPayment_isAccepted_andReturnsValidatedStatus() {
        PaymentValidationRequest request = new PaymentValidationRequest(
                "IT-PAY-" + UUID.randomUUID(),
                Scheme.INSTANT_PAYMENT,
                new BigDecimal("250.00"),
                "USD",
                "ACC-DEBTOR-IT-01",
                "BANK001",
                "ACC-CREDITOR-IT-01",
                "BANK002"
        );

        ResponseEntity<ValidationResponse> response = restTemplate.postForEntity(baseUrl(), request, ValidationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status().name()).isEqualTo("VALIDATED");
    }

    @Test
    void duplicatePaymentReference_secondRequestReturns409() {
        String reference = "IT-PAY-DUP-" + UUID.randomUUID();
        PaymentValidationRequest request = new PaymentValidationRequest(
                reference, Scheme.REAL_TIME_PAYMENT, new BigDecimal("500.00"), "USD",
                "ACC-A", "BANK001", "ACC-B", "BANK002"
        );

        ResponseEntity<ValidationResponse> first = restTemplate.postForEntity(baseUrl(), request, ValidationResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Exact same paymentReference sent again - simulates a bank's
        // network-retry behavior, the real-world scenario this whole
        // mechanism protects against.
        ResponseEntity<ValidationResponse> second = restTemplate.postForEntity(baseUrl(), request, ValidationResponse.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().status().name()).isEqualTo("DUPLICATE");
    }

    @Test
    void blacklistedDebtorAccount_isRejectedNotErrored() {
        // ACC-FRAUD-001 @ BANK001 is seeded as blacklisted in V1_0_7.
        PaymentValidationRequest request = new PaymentValidationRequest(
                "IT-PAY-BL-" + UUID.randomUUID(),
                Scheme.CARD_PAYMENT,
                new BigDecimal("100.00"),
                "USD",
                "ACC-FRAUD-001",
                "BANK001",
                "ACC-CREDITOR-CLEAN",
                "BANK002"
        );

        ResponseEntity<ValidationResponse> response = restTemplate.postForEntity(baseUrl(), request, ValidationResponse.class);

        // 200, not 4xx/5xx - a blacklist hit is a successfully-processed
        // REJECTION, not an application error. See ValidationController's
        // javadoc for the full reasoning.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status().name()).isEqualTo("REJECTED");
        assertThat(response.getBody().rejectionReason()).contains("blacklisted");
    }

    @Test
    void amountExceedingSchemeLimit_isRejected() {
        // Seeded InstantPayment limit is 500,000.00 - send comfortably over it.
        PaymentValidationRequest request = new PaymentValidationRequest(
                "IT-PAY-LIMIT-" + UUID.randomUUID(),
                Scheme.INSTANT_PAYMENT,
                new BigDecimal("999999.00"),
                "USD",
                "ACC-DEBTOR-LIMIT",
                "BANK001",
                "ACC-CREDITOR-LIMIT",
                "BANK002"
        );

        ResponseEntity<ValidationResponse> response = restTemplate.postForEntity(baseUrl(), request, ValidationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status().name()).isEqualTo("REJECTED");
        assertThat(response.getBody().rejectionReason()).contains("exceeds limit");
    }

    @Test
    void malformedRequest_missingRequiredFields_returns400() {
        // amount and currency deliberately omitted/invalid - Jakarta Bean
        // Validation should reject this BEFORE it ever reaches the service
        // layer or touches the database.
        String malformedJson = """
                {
                  "paymentReference": "IT-PAY-BAD",
                  "scheme": "INSTANT_PAYMENT",
                  "debtorAccount": "ACC-X",
                  "debtorBankId": "BANK001",
                  "creditorAccount": "ACC-Y",
                  "creditorBankId": "BANK002"
                }
                """;

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(malformedJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(), entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownParticipantBank_isRejected() {
        PaymentValidationRequest request = new PaymentValidationRequest(
                "IT-PAY-UNKNOWN-" + UUID.randomUUID(),
                Scheme.REAL_TIME_PAYMENT,
                new BigDecimal("100.00"),
                "USD",
                "ACC-X",
                "BANK-DOES-NOT-EXIST",
                "ACC-Y",
                "BANK002"
        );

        ResponseEntity<ValidationResponse> response = restTemplate.postForEntity(baseUrl(), request, ValidationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status().name()).isEqualTo("REJECTED");
        assertThat(response.getBody().rejectionReason()).contains("not a registered PaymentX participant");
    }
}
