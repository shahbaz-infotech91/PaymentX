package com.paymentx.prompt.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.prompt.dto.CreatePromptRequest;
import com.paymentx.prompt.dto.PromptResponse;
import com.paymentx.prompt.dto.PromptVariable;
import com.paymentx.prompt.dto.PromptVersionResponse;
import com.paymentx.prompt.dto.RenderPromptRequest;
import com.paymentx.prompt.dto.RenderPromptResponse;
import com.paymentx.prompt.entity.PromptType;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * English:
 * A real, end-to-end HTTP test of PromptController against a real
 * Postgres container - matching RoutingControllerIntegrationTest's
 * exact pattern. What it verifies: the full create -> activate ->
 * render happy path through real HTTP with real persistence; admin
 * endpoints reject a caller with no X-Roles header (403, real
 * AuthorizationDeniedException mapping - see
 * HeaderRoleAuthenticationFilter/SecurityConfig) and accept one with
 * PROMPT_ADMIN; a blank `key` in the create request is rejected with a
 * real 400 VALIDATION_ERROR before ever reaching PromptServiceImpl; a
 * duplicate key is rejected with a real 409; rendering with a missing
 * required variable is rejected with a real 422.
 * Why it exists: Step 25 of the Phase 3.2 brief - "authorization,
 * controller validation, global exception mapping" specifically need a
 * real HTTP-layer test, not just a service-layer unit test, since
 * @PreAuthorize/@Valid/@RestControllerAdvice are all Spring-managed
 * behavior that mocked unit tests never actually exercise.
 * How it communicates with other components: boots the real Spring
 * context (SecurityConfig, GlobalExceptionHandler, Liquibase-migrated
 * schema, the works) against an ephemeral Testcontainers Postgres -
 * the closest thing to running this service for real without actually
 * starting it standalone.
 *
 * Hinglish:
 * PromptController ka ek real, end-to-end HTTP test, ek real Postgres
 * container ke against - RoutingControllerIntegrationTest ke exact
 * pattern se match karte hue. Ye kya verify karta hai: real HTTP aur
 * real persistence ke through poora create -> activate -> render happy
 * path; admin endpoints ek aise caller ko reject karte hain jiske paas
 * X-Roles header nahi hai (403, real AuthorizationDeniedException
 * mapping - HeaderRoleAuthenticationFilter/SecurityConfig dekho) aur
 * PROMPT_ADMIN wale ko accept karte hain; create request me ek blank
 * `key` ek real 400 VALIDATION_ERROR se reject hota hai, PromptServiceImpl
 * tak pahunchne se pehle hi; ek duplicate key ek real 409 se reject
 * hota hai; ek missing required variable ke saath render ek real 422
 * se reject hota hai.
 * Ye kyu hai: Phase 3.2 brief ka Step 25 - "authorization, controller
 * validation, global exception mapping" ko specifically ek real
 * HTTP-layer test chahiye, sirf ek service-layer unit test nahi,
 * kyunki @PreAuthorize/@Valid/@RestControllerAdvice sab Spring-managed
 * behavior hai jise mocked unit tests actually kabhi exercise nahi
 * karte.
 * Dusre components se kaise communicate karta hai: real Spring context
 * boot karta hai (SecurityConfig, GlobalExceptionHandler,
 * Liquibase-migrated schema, sab kuch) ek ephemeral Testcontainers
 * Postgres ke against - is service ko standalone actually start kiye
 * bina real me chalane ke sabse kareeb wali cheez.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PromptControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("paymentx_ai_controller_test")
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

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Roles", "PROMPT_ADMIN");
        headers.add("X-Participant-Id", "test-operator");
        return headers;
    }

    @Test
    void fullLifecycle_create_activate_render_worksEndToEnd() {
        String key = "IT_LIFECYCLE_TEST";
        CreatePromptRequest createRequest = new CreatePromptRequest(key, "IT Test", "desc", PromptType.SYSTEM,
                "Payment {{paymentReference}} failed: {{errorCode}}",
                List.of(new PromptVariable("paymentReference", true, null), new PromptVariable("errorCode", true, null)));

        ResponseEntity<ApiResponse<PromptResponse>> createResponse = restTemplate.exchange(
                baseUrl("/api/v1/prompts"), HttpMethod.POST, new HttpEntity<>(createRequest, adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<PromptResponse>>() {
                });
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody().data().versionCount()).isEqualTo(1);
        assertThat(createResponse.getBody().data().activeVersion()).isNull();

        ResponseEntity<ApiResponse<PromptVersionResponse>> activateResponse = restTemplate.exchange(
                baseUrl("/api/v1/prompts/" + key + "/versions/1/activate"), HttpMethod.POST, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<PromptVersionResponse>>() {
                });
        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activateResponse.getBody().data().status().name()).isEqualTo("ACTIVE");

        RenderPromptRequest renderRequest = new RenderPromptRequest(null, Map.of("paymentReference", "PMT-1", "errorCode", "E1"));
        ResponseEntity<ApiResponse<RenderPromptResponse>> renderResponse = restTemplate.exchange(
                baseUrl("/api/v1/prompts/" + key + "/render"), HttpMethod.POST, new HttpEntity<>(renderRequest),
                new ParameterizedTypeReference<ApiResponse<RenderPromptResponse>>() {
                });
        assertThat(renderResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renderResponse.getBody().data().renderedContent()).isEqualTo("Payment PMT-1 failed: E1");
    }

    @Test
    void createPrompt_withoutAdminRole_isForbidden() {
        CreatePromptRequest request = new CreatePromptRequest("IT_NO_ROLE_TEST", "x", null, PromptType.SYSTEM, "hello", List.of());
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl("/api/v1/prompts"), HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createPrompt_blankKey_returns400ValidationError() {
        CreatePromptRequest request = new CreatePromptRequest("", "x", null, PromptType.SYSTEM, "hello", List.of());
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl("/api/v1/prompts"), HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createPrompt_duplicateKey_returns409Conflict() {
        CreatePromptRequest request = new CreatePromptRequest("IT_DUP_KEY_TEST", "x", null, PromptType.SYSTEM, "hello", List.of());
        restTemplate.exchange(baseUrl("/api/v1/prompts"), HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), Void.class);

        ResponseEntity<ApiResponse<Void>> secondResponse = restTemplate.exchange(
                baseUrl("/api/v1/prompts"), HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondResponse.getBody().error().errorCode()).isEqualTo("PROMPT_ALREADY_EXISTS");
    }

    @Test
    void getPrompt_unknownKey_returns404() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl("/api/v1/prompts/IT_DOES_NOT_EXIST"), HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error().errorCode()).isEqualTo("PROMPT_NOT_FOUND");
    }

    @Test
    void render_missingRequiredVariable_returns422() {
        String key = "IT_RENDER_MISSING_VAR_TEST";
        CreatePromptRequest createRequest = new CreatePromptRequest(key, "x", null, PromptType.SYSTEM,
                "Failed: {{errorCode}}", List.of(new PromptVariable("errorCode", true, null)));
        restTemplate.exchange(baseUrl("/api/v1/prompts"), HttpMethod.POST, new HttpEntity<>(createRequest, adminHeaders()), Void.class);
        restTemplate.exchange(baseUrl("/api/v1/prompts/" + key + "/versions/1/activate"), HttpMethod.POST,
                new HttpEntity<>(null, adminHeaders()), Void.class);

        RenderPromptRequest renderRequest = new RenderPromptRequest(null, Map.of());
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl("/api/v1/prompts/" + key + "/render"), HttpMethod.POST, new HttpEntity<>(renderRequest),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().error().errorCode()).isEqualTo("MISSING_VARIABLE");
    }
}
