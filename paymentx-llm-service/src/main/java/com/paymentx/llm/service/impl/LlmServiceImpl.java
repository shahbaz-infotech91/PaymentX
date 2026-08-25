package com.paymentx.llm.service.impl;

import com.paymentx.llm.config.LlmProperties;
import com.paymentx.llm.dto.GenerateRequest;
import com.paymentx.llm.dto.GenerateResponse;
import com.paymentx.llm.dto.LlmHealthResponse;
import com.paymentx.llm.dto.LlmUsage;
import com.paymentx.llm.exception.LlmErrorCodes;
import com.paymentx.llm.exception.LlmException;
import com.paymentx.llm.metrics.LlmMetrics;
import com.paymentx.llm.provider.LlmProvider;
import com.paymentx.llm.provider.LlmProviderRequest;
import com.paymentx.llm.provider.LlmProviderResult;
import com.paymentx.llm.service.LlmService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * English:
 * The real implementation of LlmService - deliberately thin: resolve
 * "is this even configured" up front (Step 36's no-fake-AI rule applies
 * here too - if LLM_API_KEY is unset, this throws LlmException.
 * notConfigured() immediately, before any provider call, rather than
 * letting AnthropicLlmProvider fail in a more confusing way further
 * down), delegate the actual call to whichever LlmProvider is injected
 * (today: AnthropicLlmProvider - this class never references
 * com.anthropic.* directly, per Step 2/26's isolation requirement),
 * then map the provider-agnostic LlmProviderResult into the public
 * GenerateResponse DTO while recording metrics/logs. Stateless by
 * construction (Step 24) - no field here holds request-scoped or
 * cross-request state.
 * Why it exists: PAYMENTX_PHASE_3_ARCHITECTURE.md §8's LlmService
 * layer - the piece LlmController depends on instead of talking to
 * LlmProvider directly, so provider-selection/validation/metrics logic
 * has exactly one home.
 * How it communicates with other components: implements LlmService;
 * injected into LlmController; calls LlmProvider (AnthropicLlmProvider)
 * and LlmMetrics.
 *
 * Hinglish:
 * LlmService ki real implementation - jaan-boojh kar thin: sabse pehle
 * "kya ye configured bhi hai" resolve karta hai (Step 36 ka no-fake-AI
 * rule yahan bhi lagu hota hai - agar LLM_API_KEY unset hai, ye turant
 * LlmException.notConfigured() throw karta hai, kisi provider call se
 * pehle hi, AnthropicLlmProvider ko aage ek zyada confusing tareeke se
 * fail hone dene ke bajaye), actual call jo bhi LlmProvider inject hua
 * hai use delegate karta hai (aaj: AnthropicLlmProvider - ye class
 * kabhi seedhe com.anthropic.* reference nahi karti, Step 2/26 ke
 * isolation requirement ke hisaab se), phir provider-agnostic
 * LlmProviderResult ko public GenerateResponse DTO me map karta hai
 * metrics/logs record karte hue. Construction se hi stateless (Step 24)
 * - yahan koi field request-scoped ya cross-request state nahi rakhta.
 * Ye kyu hai: PAYMENTX_PHASE_3_ARCHITECTURE.md §8 ka LlmService layer -
 * wo piece jispar LlmController depend karta hai seedhe LlmProvider se
 * baat karne ke bajaye, taaki provider-selection/validation/metrics
 * logic ka exactly ek ghar ho.
 * Dusre components se kaise communicate karta hai: LlmService implement
 * karta hai; LlmController me inject hota hai; LlmProvider
 * (AnthropicLlmProvider) aur LlmMetrics ko call karta hai.
 */
@Service
@Slf4j
public class LlmServiceImpl implements LlmService {

    private final LlmProvider llmProvider;
    private final LlmProperties properties;
    private final LlmMetrics metrics;

    public LlmServiceImpl(LlmProvider llmProvider, LlmProperties properties, LlmMetrics metrics) {
        this.llmProvider = llmProvider;
        this.properties = properties;
        this.metrics = metrics;
    }

    // Phase 5 - protects the single physical choke point every real Gemini/Anthropic call in the
    // whole PaymentX platform funnels through (agent-orchestrator's planning/synthesis calls,
    // Control Center's own AI Assistant chat, any future caller), regardless of which provider is
    // currently active (resilience4j.ratelimiter.instances.llmGenerate, application.yml). This is
    // deliberately generous enough to never throttle one legitimate agent execution's own
    // sequential planning-loop calls (up to AgentOrchestratorProperties.maxIterations=5 plus one
    // final-answer synthesis call), while still bounding a genuine flood (a UI double-click
    // bypassing the frontend's own guard, a duplicate browser tab, a runaway retry loop) from
    // burning through Gemini's 20-requests/day free-tier quota - a real, repeatedly-observed
    // failure mode this engagement has hit - in a few seconds with no operator visibility.
    // RequestNotPermitted (thrown by the Resilience4j proxy before this method body ever runs) is
    // mapped to a real, honest LLM_LOCAL_RATE_LIMITED 429 by GlobalExceptionHandler - never a
    // fabricated success, matching every other failure path in this class.
    @RateLimiter(name = "llmGenerate")
    @Override
    public GenerateResponse generate(GenerateRequest request) {
        // `provider` here is the nominal/primary provider (llmProvider is now
        // provider.LlmProviderRouter as of Phase 4.8.6, whose providerName() reports the
        // configured PRIMARY) - used for the request/failure-path metrics below, since at those
        // two points it is not yet known (request) or no longer available (failure, no result
        // object) which concrete provider actually served the call. The SUCCESS path below uses
        // result.provider() instead, which is always the real, actual serving provider (primary
        // or fallback) - see LlmProviderResult's own javadoc.
        String provider = llmProvider.providerName();
        metrics.recordRequest(provider);
        Timer.Sample timerSample = metrics.startGenerateTimer();

        LlmProviderRequest providerRequest = new LlmProviderRequest(
                request.prompt(),
                request.systemPrompt(),
                request.model(),
                request.maxTokens() != null ? request.maxTokens() : 0,
                request.temperature()
        );

        try {
            LlmProviderResult result = llmProvider.generate(providerRequest);
            String servedBy = result.provider();
            metrics.stopGenerateTimer(timerSample, servedBy);
            metrics.recordTokens(servedBy, result.model(), result.inputTokens(), result.outputTokens());
            if (result.refused()) {
                metrics.recordRefused(servedBy, result.model());
                log.info("LLM call completed with a refusal provider={} model={} fallbackUsed={} latencyMs={}",
                        servedBy, result.model(), result.fallbackUsed(), result.latencyMs());
            } else {
                metrics.recordSuccess(servedBy, result.model());
                log.info("LLM call completed provider={} model={} stopReason={} fallbackUsed={} fallbackReason={} "
                                + "inputTokens={} outputTokens={} latencyMs={}",
                        servedBy, result.model(), result.stopReason(), result.fallbackUsed(), result.fallbackReason(),
                        result.inputTokens(), result.outputTokens(), result.latencyMs());
            }
            return toResponse(result);
        } catch (LlmException ex) {
            metrics.stopGenerateTimer(timerSample, provider);
            metrics.recordFailure(provider, ex.getErrorCode());
            metrics.recordProviderError(provider, ex.getErrorCode());
            if (LlmErrorCodes.LLM_NOT_CONFIGURED.equals(ex.getErrorCode())) {
                metrics.recordNotConfigured();
            }
            throw ex;
        }
    }

    @Override
    public LlmHealthResponse health() {
        // Phase 4.8.4 - provider-aware: reads whichever config block matches
        // the actually-active LlmProvider bean instead of always assuming
        // Anthropic, now that a second provider (Gemini) exists. Falls back
        // to the Anthropic block for any provider name other than "gemini",
        // preserving this method's exact prior behavior for every existing
        // Anthropic-only test/environment.
        // Phase 5 - added an explicit "groq" branch (previously implicit-fell-through to the
        // Anthropic else branch, which would have been wrong had llm.provider ever been set to
        // "groq" - Groq is intended as a fallback, not primary, but this method should still
        // report correctly regardless of which provider is configured as primary). Same reasoning
        // for the "openai" branch added alongside it - OpenAI is intended as the last-resort
        // fallback, never primary in any environment this service currently ships, but health()
        // must still report correctly if it ever were.
        String apiKey;
        String model;
        if ("gemini".equals(llmProvider.providerName())) {
            LlmProperties.Gemini config = properties.getGemini();
            apiKey = config.getApiKey();
            model = config.getModel();
        } else if ("groq".equals(llmProvider.providerName())) {
            LlmProperties.Groq config = properties.getGroq();
            apiKey = config.getApiKey();
            model = config.getModel();
        } else if ("openai".equals(llmProvider.providerName())) {
            LlmProperties.OpenAi config = properties.getOpenai();
            apiKey = config.getApiKey();
            model = config.getModel();
        } else {
            LlmProperties.Anthropic config = properties.getAnthropic();
            apiKey = config.getApiKey();
            model = config.getModel();
        }
        boolean apiKeyPresent = apiKey != null && !apiKey.isBlank();
        String status = apiKeyPresent ? "CONFIGURED" : "NOT_CONFIGURED";
        metrics.recordHealthCheck(status);
        return new LlmHealthResponse(
                status,
                llmProvider.providerName(),
                model,
                apiKeyPresent,
                "This check confirms whether an API key is configured, not whether it is valid or whether the "
                        + "provider is currently reachable - Phase 3.3 deliberately does not make a real (billed) "
                        + "API call just to answer a health check. Call POST /api/v1/llm/generate for a real signal.",
                OffsetDateTime.now(),
                allProviderConfigStatuses()
        );
    }

    // Phase 5 (Multi-Provider LLM Resilience Expansion) - reports the SAME apiKeyPresent/
    // configuredModel signal the primary fields above already give, for every registered
    // provider, not just whichever is currently primary. Never includes the key itself.
    private java.util.List<LlmHealthResponse.ProviderConfigStatus> allProviderConfigStatuses() {
        LlmProperties.Gemini gemini = properties.getGemini();
        LlmProperties.Anthropic anthropic = properties.getAnthropic();
        LlmProperties.Groq groq = properties.getGroq();
        LlmProperties.OpenAi openai = properties.getOpenai();
        return java.util.List.of(
                new LlmHealthResponse.ProviderConfigStatus("gemini",
                        gemini.getApiKey() != null && !gemini.getApiKey().isBlank(), gemini.getModel()),
                new LlmHealthResponse.ProviderConfigStatus("anthropic",
                        anthropic.getApiKey() != null && !anthropic.getApiKey().isBlank(), anthropic.getModel()),
                new LlmHealthResponse.ProviderConfigStatus("groq",
                        groq.getApiKey() != null && !groq.getApiKey().isBlank(), groq.getModel()),
                // Phase 5 (OpenAI last-resort paid fallback) - reports the same honest
                // apiKeyPresent/configuredModel signal as every other provider; never the key
                // itself.
                new LlmHealthResponse.ProviderConfigStatus("openai",
                        openai.getApiKey() != null && !openai.getApiKey().isBlank(), openai.getModel())
        );
    }

    private GenerateResponse toResponse(LlmProviderResult result) {
        LlmUsage usage = new LlmUsage(
                result.inputTokens(),
                result.outputTokens(),
                result.cacheCreationInputTokens(),
                result.cacheReadInputTokens()
        );
        return new GenerateResponse(
                result.provider(),
                result.model(),
                result.content(),
                result.stopReason(),
                result.refused(),
                usage,
                result.latencyMs(),
                result.fallbackUsed(),
                result.fallbackReason()
        );
    }
}
