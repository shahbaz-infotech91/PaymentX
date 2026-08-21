package com.paymentx.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * English:
 * The single typed configuration surface for this service's downstream
 * URLs, embedding model selection, search/context bounds, and relevance
 * threshold - binds every rag.* key from application.yml.
 * `embeddingProvider`/`embeddingModel` (Step 10 - model compatibility)
 * are the ONE source of truth passed to BOTH the Embedding Service call
 * and Vector Service's search call, so the two are always consistent by
 * construction - never two independently-configured values that could
 * drift apart. `maxTopK` (Step 11 - "defaultTopK = 5, maximumTopK = 20")
 * is deliberately much smaller than Vector Service's own maxTopK (100,
 * Phase 3.5) - Vector Service's ceiling protects the database from an
 * expensive search; this ceiling protects the LLM's context budget from
 * an expensive prompt, a distinct concern with a distinct, tighter
 * number. `minScore` (Step 12) is RAG Service's OWN relevance gate,
 * applied client-side in RagServiceImpl AFTER Vector Service's search
 * returns its raw top-K - not delegated to Vector Service's own
 * `minScore` search parameter, specifically so RagServiceImpl can see
 * (and count, for the rag_relevance_threshold_rejections metric) the
 * candidates that were rejected, not just the ones that survived.
 * `maxContextChunks`/`maxContextCharacters` (Step 14) bound what
 * ContextBuilder ever sends to the LLM - character-based, not
 * token-based, because no real tokenizer is available anywhere in this
 * platform (Step 14 - "do NOT fabricate token counts").
 * Why it exists: Step 10/11/12/14's exact configuration surface.
 * How it communicates with other components: bound automatically via
 * @ConfigurationPropertiesScan on RagServiceApplication; injected into
 * RagServiceImpl and ContextBuilder.
 *
 * Hinglish:
 * Is service ke downstream URLs, embedding model selection, search/
 * context bounds, aur relevance threshold ke liye ek hi typed
 * configuration surface - application.yml ke har rag.* key ko bind
 * karta hai. `embeddingProvider`/`embeddingModel` (Step 10 - model
 * compatibility) EK source of truth hain jo dono Embedding Service call
 * aur Vector Service ke search call ko pass hote hain, taaki dono
 * construction se hi hamesha consistent rahein - kabhi do
 * independently-configured values nahi jo alag drift kar sakein.
 * `maxTopK` (Step 11 - "defaultTopK = 5, maximumTopK = 20") jaan-boojh
 * kar Vector Service ke apne maxTopK (100, Phase 3.5) se kaafi chhota
 * hai - Vector Service ka ceiling database ko ek expensive search se
 * protect karta hai; ye ceiling LLM ke context budget ko ek expensive
 * prompt se protect karta hai, ek alag concern ek alag, tighter number
 * ke saath. `minScore` (Step 12) RAG Service ka apna relevance gate
 * hai, RagServiceImpl me client-side apply hota hai Vector Service ke
 * search apna raw top-K return karne ke BAAD - Vector Service ke apne
 * `minScore` search parameter ko delegate nahi kiya gaya, specifically
 * taaki RagServiceImpl un candidates ko dekh sake (aur count kar sake,
 * rag_relevance_threshold_rejections metric ke liye) jo reject hue,
 * sirf jo survive hue unhe nahi. `maxContextChunks`/`maxContextCharacters`
 * (Step 14) bound karte hain ki ContextBuilder LLM ko kabhi kya bhejta
 * hai - character-based, token-based nahi, kyunki is poore platform me
 * kahin bhi koi real tokenizer available nahi hai (Step 14 - "token
 * counts fabricate MAT karo").
 * Ye kyu hai: Step 10/11/12/14 ka exact configuration surface.
 * Dusre components se kaise communicate karta hai:
 * RagServiceApplication ke @ConfigurationPropertiesScan se automatically
 * bind hota hai; RagServiceImpl aur ContextBuilder me inject hota hai.
 */
@ConfigurationProperties(prefix = "rag")
@Data
public class RagProperties {

    private String embeddingServiceUrl = "http://localhost:8094";
    private String vectorServiceUrl = "http://localhost:8095";
    private String promptServiceUrl = "http://localhost:8092";
    private String llmServiceUrl = "http://localhost:8093";

    // Phase 3.10.2 RAG audit layer - same real, already-existing Audit Service (Phase 1) McpAuditClient
    // already calls, and the same auditWriteConnectTimeoutMs/auditWriteReadTimeoutMs default values
    // McpGatewayProperties already uses, kept short and deliberately un-retried so a downed Audit Service
    // can never materially degrade real RAG query latency (see RagAuditClient's fail-open javadoc).
    private String auditServiceUrl = "http://localhost:8085";
    private long auditWriteConnectTimeoutMs = 2000;
    private long auditWriteReadTimeoutMs = 3000;

    // Phase 3.5 vector database migration: matches Phase 3.4's local Embedding Service default.
    private String embeddingProvider = "local";
    private String embeddingModel = "sentence-transformers/all-MiniLM-L6-v2";

    private String ragPromptKey = "PAYMENTX_KNOWLEDGE_ASSISTANT";

    private int defaultTopK = 5;
    private int maxTopK = 20;
    private double minScore = 0.5;

    private int maxContextChunks = 5;
    private int maxContextCharacters = 8000;

    private long embeddingConnectTimeoutMs = 3000;
    private long embeddingReadTimeoutMs = 10000;
    private long vectorConnectTimeoutMs = 3000;
    private long vectorReadTimeoutMs = 10000;
    private long promptConnectTimeoutMs = 3000;
    private long promptReadTimeoutMs = 10000;
    private long llmConnectTimeoutMs = 3000;
    private long llmReadTimeoutMs = 60000;
}
