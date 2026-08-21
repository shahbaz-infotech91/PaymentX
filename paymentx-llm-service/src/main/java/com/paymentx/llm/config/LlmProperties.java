package com.paymentx.llm.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * English:
 * The single typed configuration surface for this service's LLM
 * provider - binds every llm.* key from application.yml. `apiKey` has
 * NO hardcoded default anywhere in this class or in any *.yml file in
 * this module (see application.yml's `${LLM_API_KEY:}` - empty
 * placeholder default, not a fake key, so this service can still start
 * and honestly report NOT_CONFIGURED via GET /api/v1/llm/health rather
 * than crashing at boot when the env var is absent, matching Phase
 * 3.1's AiChatService "always start, always report truthfully"
 * pattern). `@ToString.Exclude` on apiKey matches
 * ControlCenterProperties.Postgres.password's exact pattern - this
 * value must never appear in a log line, actuator endpoint, or
 * exception message anywhere in this service.
 * Why it exists: Step 26/32 of the Phase 3.3 brief - "fully externalize
 * config... no hardcoded secrets anywhere (source, YAML, tests, logs,
 * README, Docker, frontend)."
 * How it communicates with other components: bound automatically via
 * @ConfigurationPropertiesScan on LlmServiceApplication; injected into
 * AnthropicLlmProvider (to build the SDK client and populate each
 * request) and LlmServiceImpl (health()'s apiKeyPresent/configuredModel
 * fields).
 *
 * Hinglish:
 * Is service ke LLM provider ke liye ek hi typed configuration surface
 * - application.yml ke har llm.* key ko bind karta hai. `apiKey` ka is
 * class me ya is module ki kisi *.yml file me koi hardcoded default
 * NAHI hai (application.yml ka `${LLM_API_KEY:}` dekho - ek empty
 * placeholder default, ek fake key nahi, taaki ye service tab bhi start
 * ho sake aur GET /api/v1/llm/health ke through honestly NOT_CONFIGURED
 * report kare jab env var absent ho, boot par crash hone ke bajaye -
 * Phase 3.1 ke AiChatService ke "hamesha start ho, hamesha truthfully
 * report karo" pattern se match karte hue). apiKey par
 * `@ToString.Exclude` ControlCenterProperties.Postgres.password ke
 * exact pattern se match karta hai - ye value is service me kabhi kisi
 * log line, actuator endpoint, ya exception message me nahi aani
 * chahiye.
 * Ye kyu hai: Phase 3.3 brief ka Step 26/32 - "config poori tarah
 * externalize karo... koi hardcoded secrets kahin bhi nahi (source,
 * YAML, tests, logs, README, Docker, frontend)."
 * Dusre components se kaise communicate karta hai:
 * LlmServiceApplication ke @ConfigurationPropertiesScan se automatically
 * bind hota hai; AnthropicLlmProvider (SDK client banane aur har
 * request populate karne ke liye) aur LlmServiceImpl (health()'s
 * apiKeyPresent/configuredModel fields) me inject hota hai.
 */
@ConfigurationProperties(prefix = "llm")
@Data
public class LlmProperties {

    private String provider = "anthropic";

    @NestedConfigurationProperty
    private Anthropic anthropic = new Anthropic();

    @Data
    public static class Anthropic {
        @ToString.Exclude
        private String apiKey;
        private String model = "claude-opus-5";
        private String baseUrl;
        private long defaultMaxTokens = 4096;
        private long timeoutSeconds = 60;
    }
}
