package com.paymentx.vector.exception;

/**
 * English:
 * Vector Service's own business error codes - deliberately NOT added to
 * paymentx-common-library's ErrorCodes (see that class's javadoc and
 * ADR 0004: shared, cross-cutting codes only; service-owned business
 * codes stay defined where they are thrown - matches Prompt/LLM/
 * Embedding Service's identical XxxErrorCodes pattern exactly).
 * VECTOR_DIMENSION_MISMATCH is this service's equivalent of Embedding
 * Service's EMBEDDING_DIMENSION_MISMATCH - Step 22 of the Phase 3.5
 * brief's exact required code name.
 * Why it exists: honest, specific failure reporting instead of a
 * generic "vector operation failed" code.
 * How it communicates with other components: passed into VectorException
 * at every throw site in VectorStoreServiceImpl; surfaced verbatim as
 * ErrorResponse.errorCode by GlobalExceptionHandler.
 *
 * Hinglish:
 * Vector Service ke apne business error codes - jaan-boojh kar
 * paymentx-common-library ke ErrorCodes me add NAHI kiye gaye (us
 * class ka javadoc aur ADR 0004 dekho: sirf shared, cross-cutting
 * codes; service-owned business codes wahin defined rehte hain jahan
 * throw hote hain - Prompt/LLM/Embedding Service ke identical
 * XxxErrorCodes pattern se exactly match karta hai).
 * VECTOR_DIMENSION_MISMATCH is service ka Embedding Service ke
 * EMBEDDING_DIMENSION_MISMATCH jaisa equivalent hai - Phase 3.5 brief
 * ke Step 22 ka exact required code name.
 * Ye kyu hai: honest, specific failure reporting, ek generic "vector
 * operation failed" code ke bajaye.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl
 * me har throw site par VectorException me pass hota hai;
 * GlobalExceptionHandler dwara ErrorResponse.errorCode ke roop me
 * verbatim surface hota hai.
 */
public final class VectorErrorCodes {

    private VectorErrorCodes() {
    }

    public static final String DOCUMENT_NOT_FOUND = "DOCUMENT_NOT_FOUND";
    public static final String CHUNK_NOT_FOUND = "CHUNK_NOT_FOUND";
    public static final String VECTOR_DIMENSION_MISMATCH = "VECTOR_DIMENSION_MISMATCH";
    public static final String VECTOR_INVALID_VALUE = "VECTOR_INVALID_VALUE";
    public static final String INVALID_TOP_K = "INVALID_TOP_K";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
}
