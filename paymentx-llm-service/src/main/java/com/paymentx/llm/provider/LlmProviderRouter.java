package com.paymentx.llm.provider;

import com.paymentx.llm.config.LlmProperties;
import com.paymentx.llm.exception.LlmException;
import com.paymentx.llm.metrics.LlmMetrics;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 4.8.6 - the ONE LlmProvider bean LlmServiceImpl actually calls (@Primary resolves the
 * otherwise-ambiguous injection now that AnthropicLlmProvider/GeminiLlmProvider are both
 * unconditionally-registered beans of the same type). Adds automatic primary/fallback failover
 * entirely additively, on top of the existing LlmProvider contract - LlmServiceImpl, LlmController,
 * and every caller of this service's REST API are completely unaware this class exists; they see
 * the exact same generate()-in, LlmProviderResult-out shape as when only one provider existed.
 *
 * Deliberately does NOT carry its own @CircuitBreaker/@Retry - it calls each concrete provider's
 * own already-annotated generate() method directly, so all real resilience state (failure counts,
 * OPEN/HALF_OPEN/CLOSED, retry attempts/backoff) lives exactly where it already did, per provider,
 * via their own llmProvider-gemini/llmProvider-anthropic Resilience4j instances. This class adds
 * zero additional retrying of its own - wrapping an already-retried call in another retry layer
 * would be a real retry-storm risk the brief explicitly warns against.
 *
 * Recovery is implicit, not a separate mechanism: every single request tries the PRIMARY provider
 * first, every time - never "sticky" to whichever provider served the last request. If Gemini's
 * circuit is OPEN, calling it throws CallNotPermittedException immediately (no real network call,
 * no quota spent) and this class falls to the fallback for that one request; the very next request
 * tries Gemini again, and Resilience4j's own automatic-transition-from-open-to-half-open-enabled
 * setting governs exactly when that next attempt is a real HALF_OPEN trial vs. an immediate
 * rejection. The moment Gemini succeeds again (whether from CLOSED or a HALF_OPEN trial), it is
 * primary again for that and every subsequent request - there is no persisted "currently degraded"
 * flag to reset, so there is no way for this class to get stuck on the fallback provider.
 *
 * Fallback is attempted ONLY when the primary's own failure is genuinely transient
 * (LlmException.isRetryable() == true - rate-limited/timeout/provider-unavailable/circuit-open,
 * the exact same flag config/ResilienceConfig.java's retry predicate already uses) or the
 * fallback-provider is unconfigured/misconfigured (self-fallback, unregistered name). A
 * non-retryable primary failure (invalid request, credentials rejected, not configured) is
 * rethrown immediately, unmodified, with zero fallback attempt - this is a deliberate application/
 * configuration-level failure, not a provider-availability problem, and silently retrying it on a
 * second provider would risk masking a real PaymentX-side defect rather than genuinely recovering
 * from one.
 */
@Component
@Primary
@Slf4j
public class LlmProviderRouter implements LlmProvider {

    private final List<LlmProvider> providers;
    private final LlmProperties properties;
    private final LlmMetrics metrics;
    // Phase 5 (Multi-Provider LLM Resilience Expansion) - was a single AtomicBoolean when only one
    // fallback name could ever be misconfigured as self-fallback; now a per-name set since an
    // ordered fallback chain (e.g. [anthropic, groq]) could have any individual entry
    // misconfigured independently of the others.
    private final Set<String> selfFallbackWarned = ConcurrentHashMap.newKeySet();

    public LlmProviderRouter(List<LlmProvider> providers, LlmProperties properties, LlmMetrics metrics) {
        this.providers = providers;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public String providerName() {
        return properties.getProvider();
    }

    @Override
    public LlmProviderResult generate(LlmProviderRequest request) {
        String primaryName = properties.getProvider();
        LlmProvider primary = resolve(primaryName);
        if (primary == null) {
            throw LlmException.notConfigured(
                    "No LLM provider is registered for the configured primary provider '" + primaryName + "'.");
        }

        try {
            return invoke(primary, request);
        } catch (LlmException primaryFailure) {
            if (!primaryFailure.isRetryable()) {
                // Invalid request / credentials rejected / not configured - an application or
                // configuration problem, never a provider-availability problem. Never masked by
                // silently trying a second provider.
                throw primaryFailure;
            }
            return attemptFallback(request, primaryName, primaryFailure);
        }
    }

    // Phase 5 (Multi-Provider LLM Resilience Expansion) - walks an ORDERED LIST of fallback
    // providers (was a single fallback in Phase 4.8.6), stopping at the first one that succeeds.
    // Reuses the exact same LlmException.isRetryable() semantics at EVERY hop, not just the
    // primary's: a non-retryable failure from ANY provider in the chain (invalid request/
    // credentials rejected/not configured) stops the whole chain immediately and propagates that
    // specific failure unmodified - the same "never mask a real application/configuration
    // problem by silently trying another provider" reasoning the original single-fallback design
    // already established, now applied uniformly regardless of chain position. A retryable
    // failure at any hop moves on to the next configured name; the list being exhausted
    // propagates the LAST failure encountered. `fallbackReason` on a successful result always
    // reflects the ORIGINAL primary failure's errorCode (never an intermediate hop's), matching
    // this task's own example (provider=groq, fallbackUsed=true,
    // fallbackReason=GEMINI_QUOTA_EXHAUSTED) - that is the root cause the whole chain was
    // triggered by, regardless of how many hops it took to recover.
    private LlmProviderResult attemptFallback(LlmProviderRequest request, String primaryName, LlmException primaryFailure) {
        LlmProperties.Routing routing = properties.getRouting();
        if (!routing.isFallbackEnabled()) {
            throw primaryFailure;
        }

        Set<String> attempted = ConcurrentHashMap.newKeySet();
        attempted.add(primaryName.toLowerCase());
        LlmException lastFailure = primaryFailure;

        for (String fallbackName : routing.getFallbackProviders()) {
            if (fallbackName == null || fallbackName.isBlank()) {
                continue;
            }
            if (!attempted.add(fallbackName.toLowerCase())) {
                if (selfFallbackWarned.add(fallbackName.toLowerCase())) {
                    log.warn("LLM fallback-provider '{}' is already in the failover chain (same as the primary or an "
                                    + "earlier fallback) - skipped to avoid looping a provider onto itself. Configure "
                                    + "LLM_FALLBACK_PROVIDERS with genuinely distinct provider names to enable real "
                                    + "failover to this entry.", fallbackName);
                }
                continue;
            }

            LlmProvider fallback = resolve(fallbackName);
            if (fallback == null) {
                log.warn("LLM fallback provider '{}' is not a registered provider on this deployment - skipping to "
                        + "the next configured fallback (if any).", fallbackName);
                continue;
            }

            log.warn("LLM primary provider={} failed with a retryable errorCode={} - attempting fallback provider={}.",
                    primaryName, lastFailure.getErrorCode(), fallbackName);
            metrics.recordFallback(primaryName, fallbackName, primaryFailure.getErrorCode());

            try {
                LlmProviderResult fallbackResult = invoke(fallback, request);
                log.info("LLM fallback provider={} succeeded after primary={} failure errorCode={}.",
                        fallbackName, primaryName, primaryFailure.getErrorCode());
                return withFallbackInfo(fallbackResult, primaryFailure.getErrorCode());
            } catch (LlmException fallbackFailure) {
                if (!fallbackFailure.isRetryable()) {
                    log.warn("LLM fallback provider={} failed with a NON-retryable errorCode={} - stopping the "
                                    + "failover chain immediately rather than masking a real configuration problem.",
                            fallbackName, fallbackFailure.getErrorCode());
                    throw fallbackFailure;
                }
                log.warn("LLM fallback provider={} failed with a retryable errorCode={} - trying the next configured "
                        + "fallback (if any).", fallbackName, fallbackFailure.getErrorCode());
                lastFailure = fallbackFailure;
            }
        }

        log.warn("LLM failover chain exhausted - every provider (primary={} plus all configured fallbacks) failed; "
                + "propagating the last failure errorCode={}.", primaryName, lastFailure.getErrorCode());
        throw lastFailure;
    }

    // Normalizes a circuit-breaker rejection into the same LlmException taxonomy every other
    // failure in this service already uses, so callers of this class only ever need to catch one
    // exception type - a real HTTP 429/5xx/timeout and "the circuit is currently OPEN" are both
    // genuinely transient provider-unavailability conditions from a caller's point of view.
    private LlmProviderResult invoke(LlmProvider provider, LlmProviderRequest request) {
        try {
            return provider.generate(request);
        } catch (CallNotPermittedException circuitOpen) {
            throw LlmException.providerUnavailable(
                    provider.providerName() + " provider is temporarily unavailable (circuit breaker open): " + circuitOpen.getMessage());
        }
    }

    private LlmProvider resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return providers.stream().filter(p -> name.equalsIgnoreCase(p.providerName())).findFirst().orElse(null);
    }

    private LlmProviderResult withFallbackInfo(LlmProviderResult result, String reason) {
        return new LlmProviderResult(result.provider(), result.model(), result.content(), result.stopReason(),
                result.refused(), result.inputTokens(), result.outputTokens(), result.cacheCreationInputTokens(),
                result.cacheReadInputTokens(), result.latencyMs(), true, reason);
    }
}
