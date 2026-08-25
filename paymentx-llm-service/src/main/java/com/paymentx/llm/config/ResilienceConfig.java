package com.paymentx.llm.config;

import com.paymentx.llm.exception.LlmException;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * English:
 * Supplies the one piece of the `llmProvider` Resilience4j retry
 * instance that YAML cannot express: WHICH failures are actually
 * retried. application.yml's resilience4j.retry.instances.llmProvider
 * sets max-attempts/wait-duration/backoff (identical shape to
 * payment-service's routingService instance); this class sets the
 * decision function on top - retry only when the thrown exception is
 * an LlmException whose `retryable` flag (decided once, at the moment
 * AnthropicLlmProvider mapped a typed com.anthropic.errors.* exception
 * into an LlmException - see that class's javadoc) is true. WHY not
 * YAML's retry-exceptions/ignore-exceptions: those lists match by
 * exception CLASS - every failure this service throws is the same
 * class (LlmException), so a class list can never express "retry this
 * one, not that one" the way this predicate does; using two exception
 * subclasses instead (LlmTransientException/LlmPermanentException)
 * would make the class-list approach work, but would recreate the
 * PromptNotFoundException-vs-generic-code problem Prompt Service's own
 * javadoc argues against: a field a class hierarchy adds nothing to.
 * Why it exists: Step 13 of the Phase 3.3 brief - "never retry auth/
 * invalid-request/content-policy failures."
 * How it communicates with other components: this bean is discovered
 * by resilience4j-spring-boot3's auto-configuration by matching its
 * registered name ("llmProvider") to the YAML instance of the same
 * name; @Retry(name = "llmProvider") on
 * AnthropicLlmProvider.generate is where the resulting RetryConfig is
 * actually applied.
 *
 * Hinglish:
 * `llmProvider` Resilience4j retry instance ka wo ek hissa deta hai jo
 * YAML express nahi kar sakti: KAUN se failures actually retry hote
 * hain. application.yml ka resilience4j.retry.instances.llmProvider
 * max-attempts/wait-duration/backoff set karta hai (payment-service ke
 * routingService instance jaisa hi shape); ye class uske upar decision
 * function set karti hai - sirf tab retry karo jab thrown exception ek
 * LlmException ho jiska `retryable` flag (ek baar, jab
 * AnthropicLlmProvider ne ek typed com.anthropic.errors.* exception ko
 * ek LlmException me map kiya tha - us class ka javadoc dekho, decide
 * hua) true ho. YAML ke retry-exceptions/ignore-exceptions KYU nahi:
 * wo lists exception CLASS se match karti hain - is service ka har
 * failure same class (LlmException) hai, isliye ek class list kabhi
 * "isko retry karo, usko mat karo" express nahi kar sakti jaise ye
 * predicate karta hai; do exception subclasses use karna (bajaye)
 * (LlmTransientException/LlmPermanentException) class-list approach ko
 * kaam karne layak banata, lekin wahi PromptNotFoundException-vs-
 * generic-code problem recreate karta jiske khilaaf Prompt Service ka
 * apna javadoc argue karta hai: ek field jisme class hierarchy kuch add
 * nahi karti.
 * Ye kyu hai: Phase 3.3 brief ka Step 13 - "auth/invalid-request/
 * content-policy failures par kabhi retry mat karo."
 * Dusre components se kaise communicate karta hai: ye bean
 * resilience4j-spring-boot3 ke auto-configuration dwara uske
 * registered naam ("llmProvider") ko usi naam ke YAML instance se match
 * karke discover hota hai; AnthropicLlmProvider.generate par
 * @Retry(name = "llmProvider") wahan hai jahan resulting RetryConfig
 * actually apply hota hai.
 */
@Configuration
public class ResilienceConfig {

    // Phase 4.8.6 - one customizer per provider-specific circuit-breaker/retry instance name
    // (was a single "llmProvider" customizer when only one provider bean was ever active at a
    // time; see AnthropicLlmProvider/GeminiLlmProvider.generate's own comments for why each
    // provider now has its own instance). Both customizers apply the exact same retryable-flag
    // predicate this class always has - only the instance name differs.
    @Bean
    public RetryConfigCustomizer llmProviderGeminiRetryConfigCustomizer() {
        return retryableExceptionCustomizer("llmProvider-gemini");
    }

    @Bean
    public RetryConfigCustomizer llmProviderAnthropicRetryConfigCustomizer() {
        return retryableExceptionCustomizer("llmProvider-anthropic");
    }

    // Phase 5 (Multi-Provider LLM Resilience Expansion) - third provider, same customizer, same
    // predicate, own independent instance name/circuit-breaker state (see application.yml).
    @Bean
    public RetryConfigCustomizer llmProviderGroqRetryConfigCustomizer() {
        return retryableExceptionCustomizer("llmProvider-groq");
    }

    // Phase 5 (OpenAI last-resort paid fallback) - fourth provider, same customizer, same
    // predicate, own independent instance name/circuit-breaker state (see application.yml).
    @Bean
    public RetryConfigCustomizer llmProviderOpenAiRetryConfigCustomizer() {
        return retryableExceptionCustomizer("llmProvider-openai");
    }

    private RetryConfigCustomizer retryableExceptionCustomizer(String instanceName) {
        return RetryConfigCustomizer.of(instanceName, builder -> builder.retryOnException(
                throwable -> throwable instanceof LlmException llmException && llmException.isRetryable()));
    }
}
