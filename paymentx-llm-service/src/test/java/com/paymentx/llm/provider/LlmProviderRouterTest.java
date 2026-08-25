package com.paymentx.llm.provider;

import com.paymentx.llm.config.LlmProperties;
import com.paymentx.llm.exception.LlmErrorCodes;
import com.paymentx.llm.exception.LlmException;
import com.paymentx.llm.metrics.LlmMetrics;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4.8.6 (single fallback), extended Phase 5 (Multi-Provider LLM Resilience Expansion, an
 * ORDERED fallback chain) - deterministic, no-real-provider-call tests for LlmProviderRouter's
 * automatic primary/fallback failover. All three providers are plain Mockito mocks of the
 * LlmProvider interface (never a real AnthropicLlmProvider/GeminiLlmProvider/GroqLlmProvider,
 * never a real Gemini/Anthropic/Groq API call, zero quota consumed anywhere) - this is the
 * router's own routing/selection logic in isolation, exactly the same pattern LlmServiceImplTest
 * already uses for LlmServiceImpl.
 *
 * CallNotPermittedException instances are constructed via a real, throwaway CircuitBreaker.of(...)
 * (the exception's own factory method requires a real CircuitBreaker instance) purely to get an
 * authentic exception shape - no Resilience4j AOP/proxying is involved anywhere in this test
 * file, matching AnthropicLlmProviderTest/GeminiLlmProviderTest/GroqLlmProviderTest's own
 * precedent of testing real behavior without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class LlmProviderRouterTest {

    @Mock
    private LlmProvider gemini;

    @Mock
    private LlmProvider anthropic;

    @Mock
    private LlmProvider groq;

    @Mock
    private LlmProvider openai;

    private LlmProperties properties;
    private LlmMetrics metrics;

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        properties.setProvider("gemini");
        properties.getRouting().setFallbackEnabled(true);
        properties.getRouting().setFallbackProviders(List.of("anthropic"));
        metrics = new LlmMetrics(new SimpleMeterRegistry());

        // Mockito's lenient() is not used deliberately - every test either uses a stub or
        // asserts it was never invoked (verify(..., never())), so an unnecessary-stubbing
        // failure would itself be a signal something is wrong with the test.
    }

    private LlmProviderRouter newRouter() {
        return new LlmProviderRouter(List.of(gemini, anthropic, groq, openai), properties, metrics);
    }

    private LlmProviderRequest request() {
        return new LlmProviderRequest("prompt", null, null, 0, null);
    }

    private CallNotPermittedException circuitOpenException(String breakerName) {
        CircuitBreaker breaker = CircuitBreaker.of(breakerName, CircuitBreakerConfig.ofDefaults());
        return CallNotPermittedException.createCallNotPermittedException(breaker);
    }

    private LlmProviderResult result(String provider, String content) {
        return new LlmProviderResult(provider, provider + "-model", content, "STOP", false, 1, 1, null, null, 100);
    }

    // ---- 1. Primary success ----

    @Test
    void generate_geminiSucceeds_returnsGeminiResultUnmodified_noFallbackAttempted() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenReturn(new LlmProviderResult(
                "gemini", "gemini-3.7-flash", "answer", "STOP", false, 10, 5, null, null, 200));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("gemini");
        assertThat(result.content()).isEqualTo("answer");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.fallbackReason()).isNull();
        verify(anthropic, never()).generate(any());
        verify(groq, never()).generate(any());
    }

    // ---- 2. Rate limited / quota exhaustion -> fallback (single hop) ----

    @Test
    void generate_geminiRateLimited_fallsBackToAnthropic_andMarksFallbackUsed() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("quota exceeded"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenReturn(result("anthropic", "fallback answer"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("anthropic");
        assertThat(result.content()).isEqualTo("fallback answer");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED);
    }

    // ---- 3. Gemini 429 -> Anthropic ALSO fails (retryable) -> Groq succeeds (2-hop chain) ----

    @Test
    void generate_geminiRateLimited_anthropicAlsoFails_fallsBackToGroq() {
        properties.getRouting().setFallbackProviders(List.of("anthropic", "groq"));
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini quota exhausted"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenThrow(LlmException.providerUnavailable("anthropic also down"));
        when(groq.providerName()).thenReturn("groq");
        when(groq.generate(any())).thenReturn(result("groq", "groq answer"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.content()).isEqualTo("groq answer");
        assertThat(result.fallbackUsed()).isTrue();
        // fallbackReason reflects the ORIGINAL primary failure, not the intermediate hop's -
        // matches this phase's own task brief example (fallbackReason=GEMINI_QUOTA_EXHAUSTED-style
        // root cause, regardless of how many hops it took to recover).
        assertThat(result.fallbackReason()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED);
        verify(gemini).generate(any());
        verify(anthropic).generate(any());
        verify(groq).generate(any());
    }

    // ---- 4. Timeout -> fallback ----

    @Test
    void generate_geminiTimesOut_fallsBackToAnthropic() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.timeout("connection timed out"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenReturn(result("anthropic", "ok"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("anthropic");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(LlmErrorCodes.LLM_PROVIDER_TIMEOUT);
    }

    // ---- 5. Transient 5xx -> fallback ----

    @Test
    void generate_geminiServerError_fallsBackToAnthropic() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.providerUnavailable("upstream 503"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenReturn(result("anthropic", "ok"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(LlmErrorCodes.LLM_PROVIDER_UNAVAILABLE);
    }

    // ---- 6. Circuit OPEN -> fallback used without a real call reaching the provider body ----

    @Test
    void generate_geminiCircuitOpen_fallsBackWithoutHammeringGemini() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(circuitOpenException("llmProvider-gemini"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenReturn(result("anthropic", "ok"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(LlmErrorCodes.LLM_PROVIDER_UNAVAILABLE);
        verify(gemini).generate(any()); // exactly once - the router itself never retries a CircuitBreaker rejection
    }

    // ---- 7. Non-retryable primary error -> no fallback at all ----

    @Test
    void generate_geminiInvalidRequest_doesNotFallBack_propagatesOriginalException() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.invalidRequest("malformed prompt"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_INVALID_REQUEST));
        verify(anthropic, never()).generate(any());
        verify(groq, never()).generate(any());
    }

    @Test
    void generate_geminiCredentialsRejected_doesNotFallBack() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.credentialsRejected("bad key"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_CREDENTIALS_REJECTED));
        verify(anthropic, never()).generate(any());
        verify(groq, never()).generate(any());
    }

    @Test
    void generate_geminiNotConfigured_doesNotFallBack() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.notConfigured("no api key"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_NOT_CONFIGURED));
        verify(anthropic, never()).generate(any());
        verify(groq, never()).generate(any());
    }

    // ---- A non-retryable failure from an INTERMEDIATE fallback hop also stops the whole chain,
    // exactly the same reasoning as a non-retryable PRIMARY failure - never masks a real
    // application/configuration problem by silently trying yet another provider. ----

    @Test
    void generate_geminiRetryable_anthropicNonRetryable_stopsChainWithoutTryingGroq() {
        properties.getRouting().setFallbackProviders(List.of("anthropic", "groq"));
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini exhausted"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenThrow(LlmException.credentialsRejected("anthropic key rejected"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_CREDENTIALS_REJECTED));
        verify(groq, never()).generate(any());
    }

    // ---- 8/9. Anthropic (as primary) fails -> Groq (single configured fallback) ----

    @Test
    void generate_anthropicPrimaryFails_fallsBackToGroq() {
        properties.setProvider("anthropic");
        properties.getRouting().setFallbackProviders(List.of("groq"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenThrow(LlmException.rateLimited("anthropic rate limited"));
        when(groq.providerName()).thenReturn("groq");
        when(groq.generate(any())).thenReturn(result("groq", "groq answer"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.fallbackUsed()).isTrue();
        verify(gemini, never()).generate(any());
    }

    // ---- All providers fail -> honest normalized failure, nothing swallowed ----

    @Test
    void generate_allThreeProvidersFail_propagatesLastFailure_notSwallowed() {
        properties.getRouting().setFallbackProviders(List.of("anthropic", "groq"));
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini exhausted"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenThrow(LlmException.providerUnavailable("anthropic down"));
        when(groq.providerName()).thenReturn("groq");
        when(groq.generate(any())).thenThrow(LlmException.timeout("groq timed out"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_PROVIDER_TIMEOUT));
    }

    // ---- Fallback also fails (single-hop case, preserved) ----

    @Test
    void generate_bothProvidersFail_propagatesFallbacksOwnException_notSwallowed() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini exhausted"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenThrow(LlmException.providerUnavailable("anthropic also down"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_PROVIDER_UNAVAILABLE));
    }

    // ---- 10. Recovery: every call tries primary first, no stickiness to fallback ----

    @Test
    void generate_afterAFallbackCall_theNextCallTriesPrimaryAgainAndSucceeds() {
        when(gemini.providerName()).thenReturn("gemini");
        when(anthropic.providerName()).thenReturn("anthropic");
        LlmProviderRouter router = newRouter();

        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("exhausted"));
        when(anthropic.generate(any())).thenReturn(result("anthropic", "fallback"));
        LlmProviderResult first = router.generate(request());
        assertThat(first.provider()).isEqualTo("anthropic");
        assertThat(first.fallbackUsed()).isTrue();

        // doReturn(...).when(...) (not when(...).thenReturn(...)) - the mock's PREVIOUS stub
        // (thenThrow) is still active at the instant when(gemini.generate(any())) would itself
        // invoke the mock to record the new stubbing, which would throw before the new stub is
        // even set; doReturn(...) never invokes the real/stubbed method first.
        org.mockito.Mockito.doReturn(result("gemini", "recovered"))
                .when(gemini).generate(any());
        LlmProviderResult second = router.generate(request());
        assertThat(second.provider()).isEqualTo("gemini");
        assertThat(second.fallbackUsed()).isFalse();
    }

    @Test
    void generate_geminiKeepsFailing_fallbackContinuesOnEveryCall() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("still exhausted"));
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenReturn(result("anthropic", "fallback"));
        LlmProviderRouter router = newRouter();

        assertThat(router.generate(request()).fallbackUsed()).isTrue();
        assertThat(router.generate(request()).fallbackUsed()).isTrue();
    }

    // ---- 11. Provider metadata / fallbackUsed / fallbackReason on a plain successful primary
    // call (no fallback at all) ----

    @Test
    void generate_primarySucceeds_metadataReflectsNoFallback() {
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenReturn(new LlmProviderResult(
                "gemini", "gemini-3.7-flash", "answer", "STOP", false, 10, 5, null, null, 200));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("gemini");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.fallbackReason()).isNull();
    }

    // ---- Fallback disabled / misconfigured -> today's exact original behavior preserved ----

    @Test
    void generate_fallbackDisabled_primaryFailurePropagatesUnmodified() {
        properties.getRouting().setFallbackEnabled(false);
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("exhausted"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED));
        verify(anthropic, never()).generate(any());
        verify(groq, never()).generate(any());
    }

    @Test
    void generate_selfFallback_primaryEqualsFallback_behavesAsDisabled_matchesProdDefault() {
        // Reproduces this service's own prod default: llm.provider=anthropic,
        // llm.routing.fallback-providers=[anthropic] (both defaulting to "anthropic") - must NOT
        // loop a provider onto itself; must behave exactly as fallback-disabled.
        properties.setProvider("anthropic");
        properties.getRouting().setFallbackProviders(List.of("anthropic"));
        when(gemini.providerName()).thenReturn("gemini");
        when(anthropic.providerName()).thenReturn("anthropic");
        when(anthropic.generate(any())).thenThrow(LlmException.rateLimited("exhausted"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED));
    }

    @Test
    void generate_fallbackProviderNameNotRegistered_primaryFailurePropagates() {
        properties.getRouting().setFallbackProviders(List.of("bedrock")); // no LlmProvider bean named "bedrock" exists
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("exhausted"));
        when(anthropic.providerName()).thenReturn("anthropic");

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED));
        verify(anthropic, never()).generate(any());
        verify(groq, never()).generate(any());
    }

    // ---- An unregistered name in the MIDDLE of a chain is skipped, not fatal - the next real
    // entry is still tried. ----

    @Test
    void generate_middleOfChainUnregistered_skipsToNextRealFallback() {
        properties.getRouting().setFallbackProviders(List.of("bedrock", "groq"));
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("exhausted"));
        when(groq.providerName()).thenReturn("groq");
        when(groq.generate(any())).thenReturn(result("groq", "groq answer"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void providerName_reportsConfiguredPrimary_regardlessOfWhichProviderLastServedARequest() {
        properties.setProvider("gemini");
        assertThat(newRouter().providerName()).isEqualTo("gemini");
    }

    // ==================================================================================
    // Phase 5 (OpenAI last-resort paid fallback) - the full desired 4-provider chain:
    // Gemini (primary) -> Groq -> Anthropic -> OpenAI. Every provider here is still a plain
    // Mockito mock - zero real Gemini/Groq/Anthropic/OpenAI calls anywhere in this file, matching
    // this phase's explicit cost-protection requirement (no real OpenAI request may be made
    // during implementation/testing).
    // ==================================================================================

    // Deliberately sets ONLY the fallback-providers list, not a blanket providerName() stub for
    // every mock - MockitoExtension's default STRICT_STUBS mode (this file's own established
    // convention, see the "lenient() is not used deliberately" note on setUp() above) fails a test
    // that stubs a provider's providerName() but never reaches a code path that invokes it (e.g.
    // a hop the router never gets to because an earlier hop already succeeded). Each test below
    // therefore stubs providerName() only for the providers it actually expects the router to
    // resolve.
    private void fourHopChain() {
        properties.getRouting().setFallbackProviders(List.of("groq", "anthropic", "openai"));
    }

    private void stubAllFourProviderNames() {
        when(gemini.providerName()).thenReturn("gemini");
        when(groq.providerName()).thenReturn("groq");
        when(anthropic.providerName()).thenReturn("anthropic");
        when(openai.providerName()).thenReturn("openai");
    }

    // ---- 7. Gemini -> Groq -> Anthropic -> OpenAI routing (every hop genuinely fails except the
    // last) ----

    @Test
    void generate_allThreeHigherPriorityProvidersFail_fallsBackAllTheWayToOpenAi() {
        fourHopChain();
        stubAllFourProviderNames();
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini exhausted"));
        when(groq.generate(any())).thenThrow(LlmException.providerUnavailable("groq down"));
        when(anthropic.generate(any())).thenThrow(LlmException.timeout("anthropic timed out"));
        when(openai.generate(any())).thenReturn(result("openai", "last-resort answer"));

        LlmProviderResult result = newRouter().generate(request());

        // ---- 12. Provider metadata ----
        assertThat(result.provider()).isEqualTo("openai");
        assertThat(result.content()).isEqualTo("last-resort answer");
        // ---- 13. fallbackUsed ----
        assertThat(result.fallbackUsed()).isTrue();
        // ---- 14. fallbackReason (reflects the ORIGINAL primary failure, not the last hop's) ----
        assertThat(result.fallbackReason()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED);
        verify(gemini).generate(any());
        verify(groq).generate(any());
        verify(anthropic).generate(any());
        verify(openai).generate(any());
    }

    // ---- 8. OpenAI is NOT called when Gemini succeeds ----

    @Test
    void generate_geminiSucceeds_openAiNeverCalled() {
        fourHopChain();
        when(gemini.providerName()).thenReturn("gemini");
        when(gemini.generate(any())).thenReturn(result("gemini", "primary answer"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("gemini");
        assertThat(result.fallbackUsed()).isFalse();
        verify(groq, never()).generate(any());
        verify(anthropic, never()).generate(any());
        verify(openai, never()).generate(any());
    }

    // ---- 9. OpenAI is NOT called when Groq succeeds after Gemini failure ----

    @Test
    void generate_geminiFails_groqSucceeds_openAiNeverCalled() {
        fourHopChain();
        when(gemini.providerName()).thenReturn("gemini");
        when(groq.providerName()).thenReturn("groq");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini exhausted"));
        when(groq.generate(any())).thenReturn(result("groq", "groq answer"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.fallbackUsed()).isTrue();
        verify(anthropic, never()).generate(any());
        verify(openai, never()).generate(any());
    }

    // ---- 10. OpenAI is NOT called when Anthropic succeeds ----

    @Test
    void generate_geminiAndGroqFail_anthropicSucceeds_openAiNeverCalled() {
        fourHopChain();
        when(gemini.providerName()).thenReturn("gemini");
        when(groq.providerName()).thenReturn("groq");
        when(anthropic.providerName()).thenReturn("anthropic");
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini exhausted"));
        when(groq.generate(any())).thenThrow(LlmException.providerUnavailable("groq down"));
        when(anthropic.generate(any())).thenReturn(result("anthropic", "anthropic answer"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("anthropic");
        assertThat(result.fallbackUsed()).isTrue();
        verify(openai, never()).generate(any());
    }

    // ---- 11. OpenAI called only when all higher-priority providers fail ----

    @Test
    void generate_openAiOnlyReached_whenGeminiGroqAndAnthropicAllFail() {
        fourHopChain();
        stubAllFourProviderNames();
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini exhausted"));
        when(groq.generate(any())).thenThrow(LlmException.providerUnavailable("groq down"));
        when(anthropic.generate(any())).thenThrow(LlmException.timeout("anthropic timed out"));
        when(openai.generate(any())).thenReturn(result("openai", "last-resort answer"));

        LlmProviderResult result = newRouter().generate(request());

        assertThat(result.provider()).isEqualTo("openai");
        verify(openai).generate(any());
    }

    // ---- 5. OpenAI circuit OPEN -> chain falls all the way to it via a rejection, not a real
    // network call reaching any earlier provider's body twice ----

    @Test
    void generate_openAiCircuitOpen_whenReachedAsLastResort_propagatesProviderUnavailable() {
        fourHopChain();
        stubAllFourProviderNames();
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini exhausted"));
        when(groq.generate(any())).thenThrow(LlmException.providerUnavailable("groq down"));
        when(anthropic.generate(any())).thenThrow(LlmException.timeout("anthropic timed out"));
        when(openai.generate(any())).thenThrow(circuitOpenException("llmProvider-openai"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> {
                    LlmException llmEx = (LlmException) ex;
                    assertThat(llmEx.getErrorCode()).isEqualTo(LlmErrorCodes.LLM_PROVIDER_UNAVAILABLE);
                    assertThat(llmEx.isRetryable()).isTrue();
                });
    }

    // ---- OpenAI's own non-retryable failure (e.g. bad credentials) stops the chain immediately,
    // same as any other hop's non-retryable failure - the whole chain has already been exhausted
    // at this point, so there is nothing further to fall back to regardless. ----

    @Test
    void generate_openAiNonRetryableFailure_asLastHop_propagatesUnmodified() {
        fourHopChain();
        stubAllFourProviderNames();
        when(gemini.generate(any())).thenThrow(LlmException.rateLimited("gemini exhausted"));
        when(groq.generate(any())).thenThrow(LlmException.providerUnavailable("groq down"));
        when(anthropic.generate(any())).thenThrow(LlmException.timeout("anthropic timed out"));
        when(openai.generate(any())).thenThrow(LlmException.credentialsRejected("openai key rejected"));

        assertThatThrownBy(() -> newRouter().generate(request()))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_CREDENTIALS_REJECTED));
    }
}
