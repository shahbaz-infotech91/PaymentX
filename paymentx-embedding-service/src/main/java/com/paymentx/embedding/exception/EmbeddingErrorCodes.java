package com.paymentx.embedding.exception;

/**
 * English:
 * Embedding Service's own business error codes - deliberately NOT added
 * to paymentx-common-library's ErrorCodes (see that class's javadoc and
 * ADR 0004: shared, cross-cutting codes only; service-owned business
 * codes stay defined where they are thrown - matches Prompt Service's
 * PromptErrorCodes and LLM Service's LlmErrorCodes exactly). A caller
 * needs to distinguish "the provider is not configured" from "the
 * provider timed out" from "the provider returned a vector of the wrong
 * length" programmatically, not just from a human-readable message.
 * EMBEDDING_DIMENSION_MISMATCH is unique to this service (no LLM Service
 * equivalent) - see Step 12 of the Phase 3.4 brief.
 * Why it exists: honest, specific failure reporting instead of a single
 * generic "embedding failed" code.
 * How it communicates with other components: passed into
 * EmbeddingException at every throw site in OpenAiEmbeddingProvider/
 * EmbeddingServiceImpl; surfaced verbatim as ErrorResponse.errorCode by
 * GlobalExceptionHandler.
 *
 * Hinglish:
 * Embedding Service ke apne business error codes - jaan-boojh kar
 * paymentx-common-library ke ErrorCodes me add NAHI kiye gaye (us
 * class ka javadoc aur ADR 0004 dekho: sirf shared, cross-cutting
 * codes; service-owned business codes wahin defined rehte hain jahan
 * throw hote hain - Prompt Service ke PromptErrorCodes aur LLM Service
 * ke LlmErrorCodes se exactly match karta hai). Ek caller ko
 * programmatically distinguish karna hota hai ki "provider configured
 * hi nahi hai" vs "provider timeout ho gaya" vs "provider ne galat
 * length ka vector return kiya" - sirf ek human-readable message se
 * nahi. EMBEDDING_DIMENSION_MISMATCH is service ke liye unique hai
 * (koi LLM Service equivalent nahi) - Phase 3.4 brief ka Step 12 dekho.
 * Ye kyu hai: honest, specific failure reporting, ek generic "embedding
 * failed" code ke bajaye.
 * Dusre components se kaise communicate karta hai: OpenAiEmbeddingProvider/
 * EmbeddingServiceImpl me har throw site par EmbeddingException me pass
 * hota hai; GlobalExceptionHandler dwara ErrorResponse.errorCode ke roop
 * me verbatim surface hota hai.
 */
public final class EmbeddingErrorCodes {

    private EmbeddingErrorCodes() {
    }

    public static final String EMBEDDING_NOT_CONFIGURED = "EMBEDDING_NOT_CONFIGURED";
    public static final String EMBEDDING_CREDENTIALS_REJECTED = "EMBEDDING_CREDENTIALS_REJECTED";
    public static final String EMBEDDING_PROVIDER_TIMEOUT = "EMBEDDING_PROVIDER_TIMEOUT";
    public static final String EMBEDDING_RATE_LIMITED = "EMBEDDING_RATE_LIMITED";
    public static final String EMBEDDING_PROVIDER_UNAVAILABLE = "EMBEDDING_PROVIDER_UNAVAILABLE";
    public static final String EMBEDDING_INVALID_REQUEST = "EMBEDDING_INVALID_REQUEST";
    public static final String EMBEDDING_RESPONSE_INVALID = "EMBEDDING_RESPONSE_INVALID";
    public static final String EMBEDDING_DIMENSION_MISMATCH = "EMBEDDING_DIMENSION_MISMATCH";
    public static final String EMBEDDING_INTERNAL_ERROR = "EMBEDDING_INTERNAL_ERROR";
}
