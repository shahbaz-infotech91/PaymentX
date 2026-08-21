package com.paymentx.embedding.config;

import com.paymentx.embedding.exception.EmbeddingException;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * English:
 * Supplies the one piece of the `embeddingProvider` Resilience4j retry
 * instance that YAML cannot express: WHICH failures are actually
 * retried - matches LLM Service's ResilienceConfig exactly (see that
 * class's javadoc for the full rationale: every failure this service
 * throws is the same class, EmbeddingException, with a `retryable` flag
 * that differs per instance, which YAML's class-based retry-exceptions/
 * ignore-exceptions lists cannot express for a single type).
 * Why it exists: Step 14 of the Phase 3.4 brief - "retry only transient
 * failures... do NOT retry invalid request/authentication/authorization/
 * invalid API key/unsupported model/dimension mismatch."
 * How it communicates with other components: this bean is discovered by
 * resilience4j-spring-boot3's auto-configuration by matching its
 * registered name ("embeddingProvider") to the YAML instance of the
 * same name; @Retry(name = "embeddingProvider") on
 * OpenAiEmbeddingProvider.embed is where the resulting RetryConfig is
 * actually applied.
 *
 * Hinglish:
 * `embeddingProvider` Resilience4j retry instance ka wo ek hissa deta
 * hai jo YAML express nahi kar sakti: KAUN se failures actually retry
 * hote hain - LLM Service ke ResilienceConfig se exactly match karta
 * hai (poore rationale ke liye us class ka javadoc dekho: is service ka
 * har failure same class hai, EmbeddingException, ek `retryable` flag
 * ke saath jo per-instance alag hota hai, jise YAML ki class-based
 * retry-exceptions/ignore-exceptions lists ek single type ke liye
 * express nahi kar sakti).
 * Ye kyu hai: Phase 3.4 brief ka Step 14 - "sirf transient failures
 * retry karo... invalid request/authentication/authorization/invalid
 * API key/unsupported model/dimension mismatch par retry MAT karo."
 * Dusre components se kaise communicate karta hai: ye bean
 * resilience4j-spring-boot3 ke auto-configuration dwara uske registered
 * naam ("embeddingProvider") ko usi naam ke YAML instance se match
 * karke discover hota hai; OpenAiEmbeddingProvider.embed par
 * @Retry(name = "embeddingProvider") wahan hai jahan resulting
 * RetryConfig actually apply hota hai.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public RetryConfigCustomizer embeddingProviderRetryConfigCustomizer() {
        return RetryConfigCustomizer.of("embeddingProvider", builder -> builder.retryOnException(
                throwable -> throwable instanceof EmbeddingException embeddingException && embeddingException.isRetryable()));
    }
}
