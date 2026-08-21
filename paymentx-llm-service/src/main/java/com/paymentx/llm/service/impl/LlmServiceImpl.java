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

    @Override
    public GenerateResponse generate(GenerateRequest request) {
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
            metrics.stopGenerateTimer(timerSample, provider);
            metrics.recordTokens(provider, result.model(), result.inputTokens(), result.outputTokens());
            if (result.refused()) {
                metrics.recordRefused(provider, result.model());
                log.info("LLM call completed with a refusal provider={} model={} latencyMs={}",
                        provider, result.model(), result.latencyMs());
            } else {
                metrics.recordSuccess(provider, result.model());
                log.info("LLM call completed provider={} model={} stopReason={} inputTokens={} outputTokens={} latencyMs={}",
                        provider, result.model(), result.stopReason(), result.inputTokens(), result.outputTokens(), result.latencyMs());
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
        LlmProperties.Anthropic config = properties.getAnthropic();
        boolean apiKeyPresent = config.getApiKey() != null && !config.getApiKey().isBlank();
        String status = apiKeyPresent ? "CONFIGURED" : "NOT_CONFIGURED";
        metrics.recordHealthCheck(status);
        return new LlmHealthResponse(
                status,
                llmProvider.providerName(),
                config.getModel(),
                apiKeyPresent,
                "This check confirms whether an API key is configured, not whether it is valid or whether the "
                        + "provider is currently reachable - Phase 3.3 deliberately does not make a real (billed) "
                        + "API call just to answer a health check. Call POST /api/v1/llm/generate for a real signal.",
                OffsetDateTime.now()
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
                result.latencyMs()
        );
    }
}
