package com.paymentx.llm.provider;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 (Multi-Provider LLM Resilience Expansion, extended for the OpenAI last-resort paid
 * fallback) - test scenario 18 of this phase's own required list ("Independent circuit
 * breakers"). Proves the real, auto-configured CircuitBreakerRegistry (not a mock, not a fresh
 * CircuitBreakerConfig.ofDefaults() instance - the SAME registry bean the real
 * llmProvider-gemini/llmProvider-anthropic/llmProvider-groq/llmProvider-openai Resilience4j
 * instances application.yml configures and GeminiLlmProvider/AnthropicLlmProvider/
 * GroqLlmProvider/OpenAiLlmProvider's own @CircuitBreaker(name=...) annotations bind to) genuinely
 * holds four DISTINCT CircuitBreaker instances, and that forcing one OPEN never affects the
 * others' state. Deliberately does NOT drive this via 10+ real/simulated failing calls (the
 * sliding-window config's own minimum-number-of-calls) - CircuitBreaker.transitionToOpenState()
 * is the same real, public Resilience4j API the breaker's own state machine uses internally, so
 * this is a genuine state transition, not a fabricated one, achieved deterministically and fast
 * rather than by waiting out a real failure cascade. No real Gemini/Anthropic/Groq/OpenAI API
 * call is made anywhere in this test - it never reaches LlmServiceImpl.generate() at all, only
 * the resilience registry itself.
 */
@SpringBootTest
class LlmProviderCircuitBreakerIndependenceTest {

    @Autowired
    private CircuitBreakerRegistry registry;

    @Test
    void fourProviderInstances_areDistinctCircuitBreakers_notASharedOne() {
        CircuitBreaker gemini = registry.circuitBreaker("llmProvider-gemini");
        CircuitBreaker anthropic = registry.circuitBreaker("llmProvider-anthropic");
        CircuitBreaker groq = registry.circuitBreaker("llmProvider-groq");
        CircuitBreaker openai = registry.circuitBreaker("llmProvider-openai");

        assertThat(gemini).isNotSameAs(anthropic);
        assertThat(gemini).isNotSameAs(groq);
        assertThat(gemini).isNotSameAs(openai);
        assertThat(anthropic).isNotSameAs(groq);
        assertThat(anthropic).isNotSameAs(openai);
        assertThat(groq).isNotSameAs(openai);
        assertThat(gemini.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(anthropic.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(groq.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(openai.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void geminiCircuitForcedOpen_othersRemainClosed_noCrossTripping() {
        CircuitBreaker gemini = registry.circuitBreaker("llmProvider-gemini");
        CircuitBreaker anthropic = registry.circuitBreaker("llmProvider-anthropic");
        CircuitBreaker groq = registry.circuitBreaker("llmProvider-groq");
        CircuitBreaker openai = registry.circuitBreaker("llmProvider-openai");

        gemini.transitionToOpenState();

        assertThat(gemini.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(anthropic.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(groq.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(openai.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        gemini.transitionToClosedState(); // leave shared Spring context state as found
    }

    @Test
    void groqCircuitForcedOpen_othersRemainClosed_noCrossTripping() {
        CircuitBreaker gemini = registry.circuitBreaker("llmProvider-gemini");
        CircuitBreaker anthropic = registry.circuitBreaker("llmProvider-anthropic");
        CircuitBreaker groq = registry.circuitBreaker("llmProvider-groq");
        CircuitBreaker openai = registry.circuitBreaker("llmProvider-openai");

        groq.transitionToOpenState();

        assertThat(groq.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(gemini.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(anthropic.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(openai.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        groq.transitionToClosedState(); // leave shared Spring context state as found
    }

    // ---- 15. Independent circuit breakers (OpenAI-specific) ----

    @Test
    void openAiCircuitForcedOpen_othersRemainClosed_noCrossTripping() {
        CircuitBreaker gemini = registry.circuitBreaker("llmProvider-gemini");
        CircuitBreaker anthropic = registry.circuitBreaker("llmProvider-anthropic");
        CircuitBreaker groq = registry.circuitBreaker("llmProvider-groq");
        CircuitBreaker openai = registry.circuitBreaker("llmProvider-openai");

        openai.transitionToOpenState();

        assertThat(openai.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(gemini.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(anthropic.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(groq.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        openai.transitionToClosedState(); // leave shared Spring context state as found
    }
}
