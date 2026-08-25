package com.paymentx.llm.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

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

    // Phase 4.8.6 addition - multi-provider failover. `provider` above is unchanged and remains
    // the PRIMARY provider selector (exact same meaning/default as before); this block is purely
    // additive routing behavior layered on top, read only by provider.LlmProviderRouter - neither
    // AnthropicLlmProvider nor GeminiLlmProvider know this exists. `fallbackProvider` defaults to
    // "anthropic" to match this service's own prod default `provider=anthropic` would otherwise
    // create a nonsensical self-fallback (primary==fallback) in an unconfigured prod environment -
    // LlmProviderRouter treats that specific case as "fallback effectively disabled" (logged once,
    // not per-request) rather than looping a provider back onto itself, so prod's existing
    // Anthropic-only behavior is completely unchanged unless an operator explicitly sets
    // LLM_FALLBACK_PROVIDER to a different real provider name.
    @NestedConfigurationProperty
    private Routing routing = new Routing();

    @NestedConfigurationProperty
    private Anthropic anthropic = new Anthropic();

    @NestedConfigurationProperty
    private Gemini gemini = new Gemini();

    // Phase 5 (Multi-Provider LLM Resilience Expansion) addition - mirrors Anthropic/Gemini's
    // exact field shape (see Gemini's own comment for why a shared ProviderConfig interface
    // wasn't introduced). Groq's REST contract is OpenAI-Chat-Completions-compatible (POST
    // {base-url}/chat/completions, `Authorization: Bearer <key>` - verified against
    // console.groq.com/docs/api-reference before this was written), so this nested class needs
    // no fields beyond what Anthropic/Gemini already have.
    @NestedConfigurationProperty
    private Groq groq = new Groq();

    // Phase 5 (OpenAI last-resort paid fallback) - mirrors Groq/Gemini/Anthropic's exact field
    // shape. OpenAI is intentionally the LAST provider in the desired failover chain (Gemini ->
    // Groq -> Anthropic -> OpenAI, see application-dev.yml) - it is the only provider on this
    // list that draws down a funded, non-free credit balance, so it must only ever be reached
    // once every free/already-available provider has genuinely failed.
    @NestedConfigurationProperty
    private OpenAi openai = new OpenAi();

    @Data
    public static class Routing {
        private boolean fallbackEnabled = true;

        // Phase 5 (Multi-Provider LLM Resilience Expansion) - was a single String
        // `fallbackProvider` (Phase 4.8.6); widened to an ORDERED LIST so LlmProviderRouter can
        // walk more than one fallback (Gemini -> Anthropic -> Groq) without a second routing
        // mechanism. Defaults to a single-element list containing exactly what the old default
        // was ("anthropic") - every environment that never sets LLM_FALLBACK_PROVIDERS keeps the
        // EXACT prior single-fallback behavior unchanged. Spring Boot's relaxed binding converts
        // a comma-delimited env var (e.g. LLM_FALLBACK_PROVIDERS=anthropic,groq) into this list
        // automatically - no custom converter needed.
        private List<String> fallbackProviders = List.of("anthropic");
    }

    @Data
    public static class Anthropic {
        @ToString.Exclude
        private String apiKey;
        private String model = "claude-opus-5";
        private String baseUrl;
        private long defaultMaxTokens = 4096;
        private long timeoutSeconds = 60;
    }

    // Phase 4.8.4 addition (second provider) - mirrors Anthropic's exact field
    // shape so GeminiLlmProvider/LlmServiceImpl.health() can treat both
    // uniformly; kept as its own nested class rather than a shared
    // ProviderConfig interface since the two providers' real config surfaces
    // (baseUrl semantics, auth mechanism) are not actually identical and a
    // shared type would buy nothing beyond four duplicated field names.
    @Data
    public static class Gemini {
        @ToString.Exclude
        private String apiKey;
        private String model = "gemini-3.7-flash";
        private String baseUrl = "https://generativelanguage.googleapis.com";
        private long defaultMaxTokens = 4096;
        private long timeoutSeconds = 60;
    }

    // Phase 5 (Multi-Provider LLM Resilience Expansion) - low-cost/high-free-tier third provider
    // (see PAYMENTX_PHASE_5_MULTI_PROVIDER_LLM_RESILIENCE.md's own Provider Evaluation section for
    // why Groq was selected over other candidates). `model` default is a real, current Groq model
    // id (verified against console.groq.com/docs/models before this was written), not a guess.
    @Data
    public static class Groq {
        @ToString.Exclude
        private String apiKey;
        private String model = "llama-3.3-70b-versatile";
        private String baseUrl = "https://api.groq.com/openai/v1";
        private long defaultMaxTokens = 4096;
        private long timeoutSeconds = 60;
    }

    // Phase 5 (OpenAI last-resort paid fallback) - `model` defaults to a low-cost, current
    // tool-calling-capable OpenAI model (gpt-5.4-nano) rather than a flagship model, since this
    // provider is deliberately reached only as the last resort of a four-provider chain - see
    // PAYMENTX_PHASE_5_MULTI_PROVIDER_LLM_RESILIENCE.md's Model Selection section for the full
    // rationale. `apiKey` has the same empty-placeholder default every other provider's does
    // (application.yml's `${OPENAI_API_KEY:}`) even though this deployment's real key is read
    // from the pre-existing Windows user environment variable OPENAI_API_KEY at process start -
    // it is never hardcoded here or in any *.yml file in this module.
    @Data
    public static class OpenAi {
        @ToString.Exclude
        private String apiKey;
        private String model = "gpt-5.4-nano";
        private String baseUrl = "https://api.openai.com/v1";
        private long defaultMaxTokens = 4096;
        private long timeoutSeconds = 60;
    }
}
