package com.paymentx.vector.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * English:
 * The body of POST /api/v1/vector/documents - stores (or, on a repeat
 * call with the same documentKey+documentVersion, upserts - see Step
 * 16/17) one document's metadata plus its already-chunked,
 * already-embedded content. This service does NOT generate embeddings
 * (Step 14 - "this service receives an embedding vector... embedding
 * generation belongs to Phase 3.4") - every `chunks[i].embedding` here
 * must already be a real vector a caller obtained from Embedding
 * Service's POST /api/v1/embeddings. `chunks` order in this list
 * defines `chunkIndex` (0-based, assigned by VectorStoreServiceImpl
 * from list position - a caller does not supply chunkIndex directly,
 * removing an entire class of "caller sent duplicate/out-of-order
 * indices" bug).
 * Why it exists: Step 15's exact store-embedding contract.
 * How it communicates with other components: bound by
 * VectorController.storeDocument; mapped by VectorStoreServiceImpl into
 * AiDocument/AiDocumentChunk/AiDocumentEmbedding rows.
 *
 * Hinglish:
 * POST /api/v1/vector/documents ka body - ek document ka metadata plus
 * uska already-chunked, already-embedded content store (ya, same
 * documentKey+documentVersion ke saath ek repeat call par, upsert -
 * Step 16/17 dekho) karta hai. Ye service embeddings generate NAHI
 * karti (Step 14 - "ye service ek embedding vector receive karti hai...
 * embedding generation Phase 3.4 ka kaam hai") - yahan har
 * `chunks[i].embedding` already ek real vector hona chahiye jo ek
 * caller ne Embedding Service ke POST /api/v1/embeddings se liya ho. Is
 * list me `chunks` ka order `chunkIndex` define karta hai (0-based,
 * VectorStoreServiceImpl dwara list position se assign kiya jaata hai -
 * ek caller seedhe chunkIndex supply nahi karta, ek poori class ki
 * "caller ne duplicate/out-of-order indices bheji" bug hata deta hai).
 * Ye kyu hai: Step 15 ka exact store-embedding contract.
 * Dusre components se kaise communicate karta hai: VectorController.
 * storeDocument ise bind karta hai; VectorStoreServiceImpl ise
 * AiDocument/AiDocumentChunk/AiDocumentEmbedding rows me map karta hai.
 */
public record StoreDocumentRequest(

        @NotBlank(message = "documentKey must not be blank")
        @Size(max = 256, message = "documentKey must be at most 256 characters")
        String documentKey,

        @NotBlank(message = "documentName must not be blank")
        @Size(max = 512, message = "documentName must be at most 512 characters")
        String documentName,

        @NotBlank(message = "documentType must not be blank")
        @Size(max = 64, message = "documentType must be at most 64 characters")
        String documentType,

        @Size(max = 128, message = "source must be at most 128 characters")
        String source,

        @NotBlank(message = "documentVersion must not be blank")
        @Size(max = 32, message = "documentVersion must be at most 32 characters")
        String documentVersion,

        Map<String, Object> metadata,

        @NotEmpty(message = "chunks must not be empty")
        @Size(max = 500, message = "at most 500 chunks may be submitted in a single store request")
        List<@Valid ChunkInput> chunks
) {
    public record ChunkInput(

            @NotBlank(message = "content must not be blank")
            @Size(max = 20_000, message = "content must be at most 20,000 characters")
            String content,

            @NotEmpty(message = "embedding must not be empty")
            List<Float> embedding,

            @NotBlank(message = "provider must not be blank")
            @Size(max = 64, message = "provider must be at most 64 characters")
            String provider,

            @NotBlank(message = "model must not be blank")
            @Size(max = 128, message = "model must be at most 128 characters")
            String model,

            Map<String, Object> metadata
    ) {
    }
}
