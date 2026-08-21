package com.paymentx.embedding.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.embedding.config.EmbeddingProperties;
import com.paymentx.embedding.exception.EmbeddingException;
import com.paymentx.embedding.provider.EmbeddingProvider;
import com.paymentx.embedding.provider.EmbeddingProviderRequest;
import com.paymentx.embedding.provider.EmbeddingProviderResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * English:
 * The ONE and ONLY class in this service that talks OpenAI's real
 * embeddings wire format (Step 11's isolation requirement -
 * EmbeddingService/EmbeddingServiceImpl/EmbeddingController never see
 * OpenAI's raw JSON shape, only the provider-agnostic
 * EmbeddingProviderRequest/EmbeddingProviderResult). Unlike LLM
 * Service's AnthropicLlmProvider (which uses the official
 * anthropic-java SDK per the claude-api skill's mandatory-SDK rule),
 * this adapter calls OpenAI's REST embeddings endpoint
 * (`POST {baseUrl}/embeddings`) directly via Spring's RestTemplate -
 * OpenAI has no equivalent skill mandating an official Java SDK for
 * this platform, so the correct choice here is this platform's own
 * dominant outbound-HTTP convention (Control Center's HttpClientConfig,
 * notification-service's WebClientConfig, both RestTemplate-based),
 * not a third-party SDK dependency.
 * WHY each HTTP status maps to the EmbeddingException it does (Step 14
 * - never retry auth/invalid-request/dimension-mismatch failures): 401/
 * 403 (bad/revoked credentials) and 400/404/422 (the request itself is
 * malformed) will fail identically on every retry -> retryable=false;
 * 429 (rate limit) and 5xx (transient provider failure) might succeed
 * on the next attempt -> retryable=true. Response validation (Step 12)
 * happens here, not in EmbeddingServiceImpl: the `data` array's length
 * must equal the number of requested texts, each item's `embedding`
 * array length must equal the configured dimension exactly (a mismatch
 * throws EMBEDDING_DIMENSION_MISMATCH, never silently truncated/padded),
 * and every value must be finite (no NaN/Infinity survives into a
 * response this service returns). `@RateLimiter(name = "embeddingProvider")`
 * (Step 15 - "at minimum protect the provider from unbounded
 * concurrency") reuses the exact same resilience4j-spring-boot3
 * dependency and annotation-plus-YAML-instance pattern as the circuit
 * breaker/retry below it - no new infrastructure framework, just one
 * more Resilience4j instance under the same "embeddingProvider" name.
 * Why it exists: this class IS Step 3/11's "call a REAL configured
 * embedding provider... provider-specific API/SDK logic must remain
 * isolated" requirement.
 * How it communicates with other components: implements
 * EmbeddingProvider; injected into EmbeddingServiceImpl; the ONLY class
 * in this module that ever makes an outbound network call to an
 * embedding provider.
 *
 * Hinglish:
 * Is service ki ek aur sirf ek class jo OpenAI ka real embeddings wire
 * format bolti hai (Step 11 ka isolation requirement -
 * EmbeddingService/EmbeddingServiceImpl/EmbeddingController kabhi
 * OpenAI ka raw JSON shape nahi dekhte, sirf provider-agnostic
 * EmbeddingProviderRequest/EmbeddingProviderResult). LLM Service ke
 * AnthropicLlmProvider (jo claude-api skill ke mandatory-SDK rule ke
 * hisaab se official anthropic-java SDK use karta hai) ke ulat, ye
 * adapter OpenAI ka REST embeddings endpoint
 * (`POST {baseUrl}/embeddings`) seedhe Spring ke RestTemplate se call
 * karta hai - OpenAI ke paas is platform ke liye ek official Java SDK
 * mandate karne wali koi equivalent skill nahi hai, isliye yahan sahi
 * choice is platform ka apna dominant outbound-HTTP convention hai
 * (Control Center ka HttpClientConfig, notification-service ka
 * WebClientConfig, dono RestTemplate-based), ek third-party SDK
 * dependency nahi.
 * Har HTTP status wo EmbeddingException KYU map karta hai jo karta hai
 * (Step 14 - auth/invalid-request/dimension-mismatch failures par kabhi
 * retry mat karo): 401/403 (bad/revoked credentials) aur 400/404/422
 * (request khud malformed hai) har retry par identically fail honge ->
 * retryable=false; 429 (rate limit) aur 5xx (transient provider
 * failure) agle attempt par succeed ho sakte hain -> retryable=true.
 * Response validation (Step 12) yahan hoti hai, EmbeddingServiceImpl me
 * nahi: `data` array ki length requested texts ki sankhya ke barabar
 * honi chahiye, har item ka `embedding` array length exactly configured
 * dimension ke barabar hona chahiye (ek mismatch EMBEDDING_DIMENSION_MISMATCH
 * throw karta hai, kabhi silently truncate/pad nahi), aur har value
 * finite honi chahiye (koi NaN/Infinity is service ke return kiye
 * response me survive nahi karta).
 * Ye kyu hai: ye class hi Step 3/11 ki "ek REAL configured embedding
 * provider call karo... provider-specific API/SDK logic isolated rehni
 * chahiye" requirement HAI.
 * Dusre components se kaise communicate karta hai: EmbeddingProvider
 * implement karti hai; EmbeddingServiceImpl me inject hoti hai; is
 * module ki ek aur sirf ek class jo kabhi ek embedding provider ko
 * outbound network call karti hai.
 */
@Component
@Slf4j
// Phase 3.4 local-embedding migration: this provider now only activates when an operator explicitly opts
// back into it (embedding.provider=openai) - LocalEmbeddingProvider is the default (matchIfMissing=true on
// its own condition) now that this service's normal runtime path must not require EMBEDDING_API_KEY or
// call api.openai.com. This class itself is unchanged otherwise - still real, still fully functional if
// selected, per the migration brief's "do not remove unrelated OpenAI code if required elsewhere."
@ConditionalOnProperty(prefix = "embedding", name = "provider", havingValue = "openai")
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private static final String PROVIDER_NAME = "openai";

    private final EmbeddingProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiEmbeddingProvider(EmbeddingProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        EmbeddingProperties.OpenAi config = properties.getOpenai();
        // requestFactory pins plain HTTP/1.1 (SimpleClientHttpRequestFactory, JDK HttpURLConnection-based)
        // instead of Spring Boot's auto-detected java.net.http.HttpClient factory, which otherwise
        // negotiates HTTP/2 - fine over real TLS/ALPN against api.openai.com, but that same auto-detected
        // factory also negotiates HTTP/2 cleartext against WireMock's embedded Jetty in tests and
        // intermittently fails with connection resets/EOF there (a WireMock/JDK-HttpClient test-harness
        // quirk). REST APIs gain nothing from HTTP/2 multiplexing for this service's one-request-in,
        // one-response-out call shape, so pinning HTTP/1.1 unconditionally removes an entire class of
        // environment-dependent negotiation risk at zero real cost, in production and in tests alike.
        this.restTemplate = restTemplateBuilder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(config.getReadTimeoutMs()))
                .build();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public int dimension() {
        return properties.getOpenai().getDimension();
    }

    @Override
    public String configuredModel() {
        return properties.getOpenai().getModel();
    }

    @Override
    public boolean isReady() {
        String apiKey = properties.getOpenai().getApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    @CircuitBreaker(name = "embeddingProvider")
    @Retry(name = "embeddingProvider")
    @RateLimiter(name = "embeddingProvider")
    public EmbeddingProviderResult embed(EmbeddingProviderRequest request) {
        EmbeddingProperties.OpenAi config = properties.getOpenai();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw EmbeddingException.notConfigured(
                    "Embedding Service has no OpenAI API key configured (EMBEDDING_API_KEY is unset) - cannot call the provider.");
        }

        String model = request.model() != null && !request.model().isBlank() ? request.model() : config.getModel();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", request.texts().size() == 1 ? request.texts().get(0) : request.texts());
        body.put("model", model);

        String url = config.getBaseUrl() + "/embeddings";
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            long latencyMs = System.currentTimeMillis() - start;
            return toResult(response.getBody(), model, request.texts().size(), latencyMs);
        } catch (HttpStatusCodeException httpError) {
            throw mapHttpError(httpError);
        } catch (ResourceAccessException connectionFailure) {
            throw EmbeddingException.timeout("OpenAI embeddings call timed out or the connection failed: " + connectionFailure.getMessage());
        } catch (RestClientException unparseableResponse) {
            // HttpMessageConversionException and friends land here - a 200 response whose body is not even
            // syntactically valid JSON (distinct from toResult()'s checks below, which catch a
            // syntactically-valid-but-semantically-wrong body). Still a truthful, typed error - never an
            // unhandled 500 stack trace reaching the caller (Step 7 - "return a truthful error").
            throw EmbeddingException.responseInvalid("OpenAI embeddings response could not be parsed: " + unparseableResponse.getMessage());
        }
    }

    private EmbeddingException mapHttpError(HttpStatusCodeException httpError) {
        int status = httpError.getStatusCode().value();
        String message = extractErrorMessage(httpError.getResponseBodyAsString(), httpError.getMessage());
        return switch (status) {
            case 401, 403 -> EmbeddingException.credentialsRejected("OpenAI rejected the configured credentials: " + message);
            case 429 -> EmbeddingException.rateLimited("OpenAI embeddings rate limit exceeded: " + message);
            case 400, 404, 422 -> EmbeddingException.invalidRequest("OpenAI rejected the request as invalid: " + message);
            default -> status >= 500
                    ? EmbeddingException.providerUnavailable("OpenAI returned a server error " + status + ": " + message)
                    : EmbeddingException.internalError("Unexpected OpenAI HTTP status " + status + ": " + message);
        };
    }

    private String extractErrorMessage(String rawBody, String fallback) {
        try {
            JsonNode errorMessage = objectMapper.readTree(rawBody).path("error").path("message");
            return errorMessage.isMissingNode() || errorMessage.isNull() ? fallback : errorMessage.asText();
        } catch (Exception parseFailure) {
            return fallback;
        }
    }

    private EmbeddingProviderResult toResult(JsonNode body, String model, int expectedCount, long latencyMs) {
        if (body == null || !body.path("data").isArray()) {
            throw EmbeddingException.responseInvalid("OpenAI embeddings response was missing a valid 'data' array.");
        }
        JsonNode dataArray = body.path("data");
        if (dataArray.size() != expectedCount) {
            throw EmbeddingException.responseInvalid(
                    "OpenAI returned " + dataArray.size() + " embeddings for " + expectedCount + " requested texts.");
        }

        int expectedDimension = properties.getOpenai().getDimension();
        List<List<Float>> vectors = new ArrayList<>(Collections.nCopies(expectedCount, null));

        for (JsonNode item : dataArray) {
            int index = item.path("index").asInt(-1);
            JsonNode embeddingNode = item.path("embedding");
            if (index < 0 || index >= expectedCount || !embeddingNode.isArray()) {
                throw EmbeddingException.responseInvalid("OpenAI embeddings response contained a malformed item.");
            }
            if (embeddingNode.size() != expectedDimension) {
                throw EmbeddingException.dimensionMismatch("Expected a " + expectedDimension + "-dimension vector from model "
                        + model + " but received " + embeddingNode.size() + " values.");
            }

            List<Float> vector = new ArrayList<>(expectedDimension);
            for (JsonNode value : embeddingNode) {
                double raw = value.asDouble();
                if (Double.isNaN(raw) || Double.isInfinite(raw)) {
                    throw EmbeddingException.responseInvalid("OpenAI embeddings response contained a non-finite value.");
                }
                vector.add((float) raw);
            }
            vectors.set(index, vector);
        }

        return new EmbeddingProviderResult(PROVIDER_NAME, model, vectors, latencyMs);
    }
}
