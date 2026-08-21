package com.paymentx.embedding.dto;

import java.util.List;

/**
 * English:
 * The real, normalized result of one embedding call - the exact
 * contract Step 7/12 require. `embedding` is the provider's actual
 * returned vector, verbatim (never padded/truncated - see Step 12: a
 * length mismatch is a thrown EMBEDDING_DIMENSION_MISMATCH, not a
 * silent fixup) - `List&lt;Float&gt;`, not a primitive array, so this
 * record's generated equals()/hashCode()/toString() are the real,
 * value-based ones a caller/test expects (a primitive array field in a
 * record silently falls back to reference equality, a known Java
 * records footgun this type deliberately avoids). `dimension` is
 * echoed alongside the vector so a caller never has to separately
 * query configuration to know `embedding.size()` - it is always equal
 * to `embedding.size()` by the time this record is constructed, since
 * EmbeddingServiceImpl validates that invariant before building this
 * response.
 * Why it exists: Step 7 (response contract) and Step 12 (dimension
 * validation) simultaneously.
 * How it communicates with other components: built by
 * EmbeddingServiceImpl from OpenAiEmbeddingProvider's
 * EmbeddingProviderResult; returned inside
 * ApiResponse&lt;EmbeddingResponse&gt; by EmbeddingController.embed -
 * this IS the contract a future Phase 3.5 Vector Database ingestion
 * path will consume.
 *
 * Hinglish:
 * Ek embedding call ka real, normalized result - Step 7/12 ka exact
 * contract. `embedding` provider ka actual returned vector hai,
 * verbatim (kabhi padded/truncated nahi - Step 12 dekho: ek length
 * mismatch ek thrown EMBEDDING_DIMENSION_MISMATCH hai, ek silent fixup
 * nahi). `dimension` vector ke saath echo hota hai taaki ek caller ko
 * `embedding.length` jaanne ke liye alag se configuration query na
 * karni pade - jab tak ye record construct hota hai tab tak ye hamesha
 * `embedding.length` ke barabar hota hai, kyunki EmbeddingServiceImpl
 * is response banane se pehle us invariant ko validate karta hai.
 * Ye kyu hai: Step 7 (response contract) aur Step 12 (dimension
 * validation) ek saath.
 * Dusre components se kaise communicate karta hai: EmbeddingServiceImpl
 * ise OpenAiEmbeddingProvider ke EmbeddingProviderResult se banata hai;
 * EmbeddingController.embed ise ApiResponse&lt;EmbeddingResponse&gt; ke
 * andar return karta hai - yehi wo contract hai jise ek future Phase
 * 3.5 Vector Database ingestion path consume karega.
 */
public record EmbeddingResponse(
        List<Float> embedding,
        int dimension,
        String model,
        String provider
) {
}
