package com.paymentx.llm.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.paymentx.llm.config.LlmProperties;
import com.paymentx.llm.exception.LlmException;
import com.paymentx.llm.provider.LlmProvider;
import com.paymentx.llm.provider.LlmProviderRequest;
import com.paymentx.llm.provider.LlmProviderResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * English:
 * The ONE and ONLY class in this service that imports com.anthropic.*
 * (Step 2/26 of the Phase 3.3 brief's isolation requirement -
 * LlmService/LlmServiceImpl/LlmController never see an
 * AnthropicClient/Message/MessageCreateParams, only the
 * provider-agnostic LlmProviderRequest/LlmProviderResult). Uses the
 * official Anthropic Java SDK (the claude-api skill's mandatory
 * requirement - never a hand-rolled RestTemplate/HTTP call against
 * api.anthropic.com), built once at startup from LlmProperties (no
 * fromEnv() - this platform's own externalized-config convention wins
 * over the SDK's own env-var convention, so llm.anthropic.api-key ->
 * ${LLM_API_KEY} is the one real source of truth). maxRetries(0) on the
 * SDK client is deliberate: this platform's own Resilience4j
 * conventions (@Retry/@CircuitBreaker below, same
 * annotation-and-YAML-instance pattern PAYMENTX_PHASE_3_ARCHITECTURE.md
 * §8 recommends reusing from payment-service's already-configured but
 * previously-unused `routingService` instance) own all retry/circuit-
 * breaking behavior centrally and observably - letting the SDK ALSO
 * silently retry underneath would double retry attempts and make the
 * Resilience4j retry-count metrics lie about how many real HTTP
 * attempts actually happened.
 * WHY each catch block maps to the LlmException it does (Step 13 -
 * never retry auth/invalid-request/content-policy failures):
 * RateLimitException/InternalServerException/AnthropicIoException/any
 * other AnthropicServiceException are genuinely transient (the next
 * attempt might succeed) -> retryable=true; UnauthorizedException/
 * PermissionDeniedException (bad/revoked credentials) and BadRequest/
 * NotFound/UnprocessableEntity (the request itself is malformed) will
 * fail identically on every retry -> retryable=false. `stopReason ==
 * REFUSAL` is NOT caught as an exception anywhere here - see
 * LlmProviderResult/GenerateResponse's javadoc: a refusal is a
 * legitimate HTTP-200 answer, not a failure, so it flows through
 * toResult() like any other stop reason.
 * Why it exists: this class IS Step 6/26's "call a REAL configured LLM
 * provider" requirement.
 * How it communicates with other components: implements LlmProvider;
 * injected into LlmServiceImpl; the ONLY class in this module that ever
 * makes an outbound network call to an LLM.
 *
 * Hinglish:
 * Is service ki ek aur sirf ek class jo com.anthropic.* import karti
 * hai (Phase 3.3 brief ke Step 2/26 ka isolation requirement -
 * LlmService/LlmServiceImpl/LlmController kabhi AnthropicClient/
 * Message/MessageCreateParams nahi dekhte, sirf provider-agnostic
 * LlmProviderRequest/LlmProviderResult). Official Anthropic Java SDK
 * use karti hai (claude-api skill ki mandatory requirement - kabhi ek
 * hand-rolled RestTemplate/HTTP call api.anthropic.com ke against
 * nahi), startup par ek baar LlmProperties se build hoti hai (fromEnv()
 * nahi - is platform ka apna externalized-config convention SDK ke apne
 * env-var convention par jeetta hai, isliye llm.anthropic.api-key ->
 * ${LLM_API_KEY} hi ek real source of truth hai). SDK client par
 * maxRetries(0) jaan-boojh kar hai: is platform ke apne Resilience4j
 * conventions (@Retry/@CircuitBreaker neeche, wahi
 * annotation-and-YAML-instance pattern jo
 * PAYMENTX_PHASE_3_ARCHITECTURE.md §8 payment-service ke already-
 * configured lekin pehle-unused `routingService` instance se reuse
 * karne ki recommend karta hai) sab retry/circuit-breaking behavior
 * centrally aur observably own karte hain - agar SDK BHI neeche silently
 * retry kare toh retry attempts double ho jaayenge aur Resilience4j ke
 * retry-count metrics jhooth bolenge ki actually kitne real HTTP
 * attempts hue.
 * Har catch block wo LlmException KYU map karta hai jo karta hai (Step
 * 13 - auth/invalid-request/content-policy failures par kabhi retry
 * mat karo): RateLimitException/InternalServerException/
 * AnthropicIoException/koi bhi doosra AnthropicServiceException
 * genuinely transient hain (agla attempt succeed ho sakta hai) ->
 * retryable=true; UnauthorizedException/PermissionDeniedException (bad/
 * revoked credentials) aur BadRequest/NotFound/UnprocessableEntity
 * (request khud malformed hai) har retry par identically fail honge ->
 * retryable=false. `stopReason == REFUSAL` ko yahan kahin bhi exception
 * ke roop me catch nahi kiya gaya - LlmProviderResult/GenerateResponse
 * ka javadoc dekho: ek refusal ek legitimate HTTP-200 answer hai, ek
 * failure nahi, isliye wo toResult() se guzarta hai kisi bhi doosre stop
 * reason ki tarah.
 * Ye kyu hai: ye class hi Step 6/26 ki "ek REAL configured LLM provider
 * call karo" requirement HAI.
 * Dusre components se kaise communicate karta hai: LlmProvider
 * implement karti hai; LlmServiceImpl me inject hoti hai; is module ki
 * ek aur sirf ek class jo kabhi ek LLM ko outbound network call karti
 * hai.
 */
@Component
@Slf4j
public class AnthropicLlmProvider implements LlmProvider {

    private static final String PROVIDER_NAME = "anthropic";

    private final LlmProperties properties;
    private AnthropicClient client;

    public AnthropicLlmProvider(LlmProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        LlmProperties.Anthropic config = properties.getAnthropic();
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(config.getApiKey() != null ? config.getApiKey() : "")
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .maxRetries(0);
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            builder.baseUrl(config.getBaseUrl());
        }
        this.client = builder.build();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    @CircuitBreaker(name = "llmProvider")
    @Retry(name = "llmProvider")
    public LlmProviderResult generate(LlmProviderRequest request) {
        LlmProperties.Anthropic config = properties.getAnthropic();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw LlmException.notConfigured(
                    "LLM Service has no Anthropic API key configured (LLM_API_KEY is unset) - cannot call the provider.");
        }

        String model = request.model() != null && !request.model().isBlank() ? request.model() : config.getModel();
        long maxTokens = request.maxTokens() > 0 ? request.maxTokens() : config.getDefaultMaxTokens();

        MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .addUserMessage(request.prompt());
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            paramsBuilder.system(request.systemPrompt());
        }
        if (request.temperature() != null) {
            paramsBuilder.temperature(request.temperature());
        }
        MessageCreateParams params = paramsBuilder.build();

        long start = System.currentTimeMillis();
        try {
            Message message = client.messages().create(params);
            long latencyMs = System.currentTimeMillis() - start;
            return toResult(message, latencyMs);
        } catch (RateLimitException e) {
            throw LlmException.rateLimited("Anthropic provider rate limit exceeded: " + e.getMessage());
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw LlmException.credentialsRejected("Anthropic provider rejected the configured credentials: " + e.getMessage());
        } catch (BadRequestException | NotFoundException | UnprocessableEntityException e) {
            throw LlmException.invalidRequest("Anthropic provider rejected the request as invalid: " + e.getMessage());
        } catch (InternalServerException e) {
            throw LlmException.providerUnavailable("Anthropic provider returned a server error: " + e.getMessage());
        } catch (AnthropicIoException e) {
            throw LlmException.timeout("Anthropic provider call timed out or the connection failed: " + e.getMessage());
        } catch (AnthropicServiceException e) {
            throw LlmException.providerUnavailable("Anthropic provider returned an unexpected error status "
                    + e.statusCode() + ": " + e.getMessage());
        } catch (AnthropicException e) {
            throw LlmException.internalError("Unexpected Anthropic SDK error: " + e.getMessage());
        }
    }

    private LlmProviderResult toResult(Message message, long latencyMs) {
        String stopReason = message.stopReason().map(StopReason::asString).orElse("unknown");
        boolean refused = message.stopReason().map(sr -> sr.equals(StopReason.REFUSAL)).orElse(false);

        String content = message.content().stream()
                .map(ContentBlock::text)
                .flatMap(Optional::stream)
                .map(TextBlock::text)
                .collect(Collectors.joining());

        Usage usage = message.usage();

        return new LlmProviderResult(
                PROVIDER_NAME,
                message.model().asString(),
                content,
                stopReason,
                refused,
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheCreationInputTokens().orElse(null),
                usage.cacheReadInputTokens().orElse(null),
                latencyMs
        );
    }
}
