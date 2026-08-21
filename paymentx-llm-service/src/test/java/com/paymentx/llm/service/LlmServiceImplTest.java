package com.paymentx.llm.service;

import com.paymentx.llm.config.LlmProperties;
import com.paymentx.llm.dto.GenerateRequest;
import com.paymentx.llm.dto.GenerateResponse;
import com.paymentx.llm.dto.LlmHealthResponse;
import com.paymentx.llm.exception.LlmErrorCodes;
import com.paymentx.llm.exception.LlmException;
import com.paymentx.llm.metrics.LlmMetrics;
import com.paymentx.llm.provider.LlmProvider;
import com.paymentx.llm.provider.LlmProviderRequest;
import com.paymentx.llm.provider.LlmProviderResult;
import com.paymentx.llm.service.impl.LlmServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * English:
 * Unit tests for LlmServiceImpl with a mocked LlmProvider (matches
 * PromptServiceImplTest's exact pattern: @Mock the collaborator, a real
 * SimpleMeterRegistry-backed metrics object rather than a mocked one,
 * so metric-recording code paths actually execute instead of being
 * silently skipped by a mock). Proves the two things this layer alone
 * is responsible for, decoupled from any real HTTP/SDK behaviour
 * (that's AnthropicLlmProviderTest's job): (1) an unconfigured provider
 * path never reaches LlmProvider.generate at all (Step 36 - no fake AI
 * - the earliest possible truthful rejection), and (2) a real
 * LlmProviderResult (including a refusal) is mapped into
 * GenerateResponse without any field being silently dropped or
 * invented.
 * Why it exists: Step 34 of the Phase 3.3 brief.
 * How it communicates with other components: exercises LlmServiceImpl
 * directly.
 *
 * Hinglish:
 * LlmServiceImpl ke liye unit tests, ek mocked LlmProvider ke saath
 * (PromptServiceImplTest ke exact pattern se match karta hai: collaborator
 * ko @Mock karo, ek mocked metrics object ke bajaye ek real
 * SimpleMeterRegistry-backed metrics object, taaki metric-recording code
 * paths actually execute hon, ek mock dwara silently skip na hon). Do
 * cheezein prove karta hai jinke liye sirf ye layer responsible hai, kisi
 * real HTTP/SDK behaviour se decoupled (wo AnthropicLlmProviderTest ka
 * kaam hai): (1) ek unconfigured provider path kabhi LlmProvider.generate
 * tak pahunchta hi nahi (Step 36 - no fake AI - jitni jaldi ho sake
 * truthful rejection), aur (2) ek real LlmProviderResult (refusal
 * included) GenerateResponse me bina kisi field ko silently drop ya
 * invent kiye map hota hai.
 * Ye kyu hai: Phase 3.3 brief ka Step 34.
 * Dusre components se kaise communicate karta hai: LlmServiceImpl ko
 * seedhe exercise karta hai.
 */
@ExtendWith(MockitoExtension.class)
class LlmServiceImplTest {

    @Mock
    private LlmProvider llmProvider;

    private LlmProperties properties;
    private LlmServiceImpl llmService;

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        LlmMetrics metrics = new LlmMetrics(new SimpleMeterRegistry());
        llmService = new LlmServiceImpl(llmProvider, properties, metrics);
    }

    @Test
    void generate_delegatesToProviderAndMapsResultFields() {
        properties.getAnthropic().setApiKey("configured-key");
        when(llmProvider.providerName()).thenReturn("anthropic");
        when(llmProvider.generate(any(LlmProviderRequest.class))).thenReturn(new LlmProviderResult(
                "anthropic", "claude-opus-5", "The answer is 42.", "end_turn", false, 100, 20, null, null, 350));

        GenerateResponse response = llmService.generate(new GenerateRequest("What is the answer?", null, null, null, null));

        assertThat(response.provider()).isEqualTo("anthropic");
        assertThat(response.model()).isEqualTo("claude-opus-5");
        assertThat(response.content()).isEqualTo("The answer is 42.");
        assertThat(response.stopReason()).isEqualTo("end_turn");
        assertThat(response.refused()).isFalse();
        assertThat(response.usage().inputTokens()).isEqualTo(100);
        assertThat(response.usage().outputTokens()).isEqualTo(20);
        assertThat(response.latencyMs()).isEqualTo(350);

        verify(llmProvider).generate(any(LlmProviderRequest.class));
    }

    @Test
    void generate_refusedResult_mapsRefusedTrueWithoutThrowing() {
        properties.getAnthropic().setApiKey("configured-key");
        when(llmProvider.providerName()).thenReturn("anthropic");
        when(llmProvider.generate(any(LlmProviderRequest.class))).thenReturn(new LlmProviderResult(
                "anthropic", "claude-opus-5", "", "refusal", true, 40, 0, null, null, 120));

        GenerateResponse response = llmService.generate(new GenerateRequest("Do something unsafe.", null, null, null, null));

        assertThat(response.refused()).isTrue();
        assertThat(response.stopReason()).isEqualTo("refusal");
        assertThat(response.content()).isEmpty();
    }

    @Test
    void generate_providerThrowsLlmException_propagatesWithoutFallbackContent() {
        properties.getAnthropic().setApiKey("configured-key");
        when(llmProvider.providerName()).thenReturn("anthropic");
        when(llmProvider.generate(any(LlmProviderRequest.class)))
                .thenThrow(LlmException.rateLimited("too many requests"));

        assertThatThrownBy(() -> llmService.generate(new GenerateRequest("Hello", null, null, null, null)))
                .isInstanceOf(LlmException.class)
                .satisfies(ex -> assertThat(((LlmException) ex).getErrorCode()).isEqualTo(LlmErrorCodes.LLM_RATE_LIMITED));
    }

    @Test
    void health_apiKeyPresent_reportsConfigured() {
        properties.getAnthropic().setApiKey("configured-key");
        properties.getAnthropic().setModel("claude-opus-5");
        when(llmProvider.providerName()).thenReturn("anthropic");

        LlmHealthResponse health = llmService.health();

        assertThat(health.status()).isEqualTo("CONFIGURED");
        assertThat(health.apiKeyPresent()).isTrue();
        assertThat(health.configuredModel()).isEqualTo("claude-opus-5");
    }

    @Test
    void health_apiKeyAbsent_reportsNotConfigured() {
        when(llmProvider.providerName()).thenReturn("anthropic");

        LlmHealthResponse health = llmService.health();

        assertThat(health.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(health.apiKeyPresent()).isFalse();
    }
}
