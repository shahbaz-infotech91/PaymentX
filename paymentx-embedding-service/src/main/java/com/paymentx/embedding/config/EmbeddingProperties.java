package com.paymentx.embedding.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * English:
 * The single typed configuration surface for this service's embedding
 * provider - binds every embedding.* key from application.yml. Matches
 * LLM Service's LlmProperties pattern exactly: `apiKey` has NO
 * hardcoded default anywhere (see application.yml's
 * `${EMBEDDING_API_KEY:}` empty-placeholder default), so this service
 * can still start and honestly report NOT_CONFIGURED via GET
 * /api/v1/embeddings/health rather than crashing at boot when the env
 * var is absent. `dimension` is the model's real, fixed output length
 * (1536 for the configured default model, text-embedding-3-small) -
 * used both to build the request (OpenAI's embeddings API accepts an
 * optional `dimensions` truncation parameter, not set here so the
 * model's full native dimension is always returned) and to validate
 * every response (Step 12 - EmbeddingServiceImpl rejects any response
 * whose vector length does not equal this value). `@ToString.Exclude`
 * on apiKey matches LlmProperties.Anthropic.apiKey's exact pattern -
 * this value must never appear in a log line, actuator endpoint, or
 * exception message anywhere in this service.
 * Why it exists: Step 5 of the Phase 3.4 brief - fully externalized
 * config, no hardcoded secrets anywhere.
 * How it communicates with other components: bound automatically via
 * @ConfigurationPropertiesScan on EmbeddingServiceApplication; injected
 * into OpenAiEmbeddingProvider (to build the HTTP client and populate
 * each request) and EmbeddingServiceImpl (health()'s
 * apiKeyPresent/configuredModel/dimension fields, and input/batch-size
 * validation bounds).
 *
 * Hinglish:
 * Is service ke embedding provider ke liye ek hi typed configuration
 * surface - application.yml ke har embedding.* key ko bind karta hai.
 * LLM Service ke LlmProperties pattern se exactly match karta hai:
 * `apiKey` ka kahin bhi koi hardcoded default NAHI hai
 * (application.yml ka `${EMBEDDING_API_KEY:}` empty-placeholder default
 * dekho), taaki ye service tab bhi start ho sake aur GET
 * /api/v1/embeddings/health ke through honestly NOT_CONFIGURED report
 * kare, env var absent hone par boot par crash hone ke bajaye.
 * `dimension` model ki real, fixed output length hai (1536 configured
 * default model text-embedding-3-small ke liye) - request banane ke
 * liye use hota hai (OpenAI ke embeddings API ek optional `dimensions`
 * truncation parameter accept karta hai, yahan set nahi kiya gaya taaki
 * model ki poori native dimension hamesha return ho) aur har response
 * validate karne ke liye bhi (Step 12 - EmbeddingServiceImpl kisi bhi
 * aise response ko reject karta hai jiski vector length is value ke
 * barabar na ho). apiKey par `@ToString.Exclude`
 * LlmProperties.Anthropic.apiKey ke exact pattern se match karta hai -
 * ye value is service me kabhi kisi log line, actuator endpoint, ya
 * exception message me nahi aani chahiye.
 * Ye kyu hai: Phase 3.4 brief ka Step 5 - poori tarah externalized
 * config, koi hardcoded secrets kahin bhi nahi.
 * Dusre components se kaise communicate karta hai:
 * EmbeddingServiceApplication ke @ConfigurationPropertiesScan se
 * automatically bind hota hai; OpenAiEmbeddingProvider (HTTP client
 * banane aur har request populate karne ke liye) aur
 * EmbeddingServiceImpl (health()'s apiKeyPresent/configuredModel/
 * dimension fields, aur input/batch-size validation bounds) me inject
 * hota hai.
 */
@ConfigurationProperties(prefix = "embedding")
@Data
public class EmbeddingProperties {

    private boolean enabled = true;
    // Phase 3.4 local-embedding migration: "local" is now the default provider (see
    // LocalEmbeddingProvider's own @ConditionalOnProperty matchIfMissing=true) - this service's normal
    // runtime path no longer requires EMBEDDING_API_KEY. An operator can still set this back to "openai"
    // explicitly (OpenAiEmbeddingProvider remains fully functional, just no longer the default).
    private String provider = "local";

    @NestedConfigurationProperty
    private OpenAi openai = new OpenAi();

    @NestedConfigurationProperty
    private Local local = new Local();

    private int maxInputLength = 8000;
    private int maxBatchSize = 50;

    @Data
    public static class OpenAi {
        @ToString.Exclude
        private String apiKey;
        private String model = "text-embedding-3-small";
        private String baseUrl = "https://api.openai.com/v1";
        private int dimension = 1536;
        private long connectTimeoutMs = 5000;
        private long readTimeoutMs = 30000;
    }

    /**
     * Phase 3.4 local-embedding migration addition. Config surface for LocalEmbeddingProvider - a real,
     * self-hosted sentence-transformer model run in-process via Deep Java Library (DJL) + ONNX Runtime, no
     * network call and no API key. `model` is a Hugging Face model id resolved through DJL's own
     * HuggingFace/ONNX Runtime model zoo (`djl://ai.djl.huggingface.onnxruntime/{model}`) - DJL downloads
     * and caches the real ONNX weights + tokenizer files on first use (see `cacheDir`), never bundled into
     * this repository. `dimension` (384) is sentence-transformers/all-MiniLM-L6-v2's real, fixed output
     * length - see PAYMENTX_PHASE_3_4_LOCAL_EMBEDDING.md for why this model was selected and why 384 is
     * incompatible with Vector Service's existing `vector(1536)` column without a schema migration that is
     * explicitly out of this phase's scope. `maxSequenceLength` (256) matches this model's real training
     * sequence length - DJL's tokenizer truncates longer input rather than silently dropping/corrupting it.
     * `cacheDir`, left blank, means "use DJL's own default cache" (`~/.djl.ai`, OS user-profile directory,
     * always outside this Git repository) - set only if an operator wants an explicit alternate location;
     * this sets the `DJL_CACHE_DIR` system property at startup, DJL's own real, documented mechanism, not a
     * new one invented here.
     */
    @Data
    public static class Local {
        private String model = "sentence-transformers/all-MiniLM-L6-v2";
        private int dimension = 384;
        private int maxSequenceLength = 256;
        private String cacheDir = "";
    }
}
