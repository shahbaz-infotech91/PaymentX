package com.paymentx.embedding.dto;

import java.util.List;

/**
 * English:
 * One item's real outcome inside a batch embedding response - either a
 * real vector (embedding non-null, errorCode/errorMessage null) or a
 * real, specific item-level failure (embedding null, errorCode/
 * errorMessage set to one of EmbeddingErrorCodes) - never both, never
 * neither. `index` is the item's position in the original
 * BatchEmbeddingRequest.texts list, preserved even when that specific
 * item failed while its siblings succeeded (Step 16 - "return item-
 * level errors where appropriate... preserve item ordering"). WHY
 * per-item failures do not abort the whole batch: OpenAI's real batch
 * embeddings endpoint fails or succeeds as one HTTP call (it has no
 * native per-item failure mode) - the only failure this record's error
 * fields can actually represent today is a whole-request failure
 * applied uniformly to every item (e.g. every item gets
 * EMBEDDING_DIMENSION_MISMATCH if the provider's response array length
 * does not match embedding.max-batch-size) - this shape is still
 * defined per-item (not batch-level-only) so a future phase adding a
 * provider with genuine per-item batch semantics does not require a
 * response-contract change.
 * Why it exists: Step 7/16 of the Phase 3.4 brief.
 * How it communicates with other components: nested inside
 * BatchEmbeddingResponse.embeddings; built by EmbeddingServiceImpl.
 *
 * Hinglish:
 * Ek batch embedding response ke andar ek item ka real outcome - ya to
 * ek real vector (embedding non-null, errorCode/errorMessage null) ya
 * ek real, specific item-level failure (embedding null, errorCode/
 * errorMessage EmbeddingErrorCodes me se ek set) - kabhi dono nahi,
 * kabhi koi nahi. `index` original BatchEmbeddingRequest.texts list me
 * us item ki position hai, preserve hoti hai chahe wo specific item fail
 * hua ho jabki uske siblings succeed hue (Step 16 - "jahan appropriate
 * ho item-level errors return karo... item ordering preserve karo"). Per-
 * item failures poore batch ko abort KYU nahi karte: OpenAI ka real
 * batch embeddings endpoint ek hi HTTP call ke roop me fail ya succeed
 * hota hai (uska koi native per-item failure mode nahi hai) - aaj is
 * record ke error fields jo ek hi cheez actually represent kar sakte
 * hain wo hai ek whole-request failure jo uniformly har item par apply
 * hoti hai (jaise agar provider ke response array ki length embedding.
 * max-batch-size se match nahi karti toh har item ko
 * EMBEDDING_DIMENSION_MISMATCH milta hai) - ye shape phir bhi per-item
 * define ki gayi hai (sirf batch-level nahi) taaki ek future phase jo
 * genuine per-item batch semantics wala provider add kare use ek
 * response-contract change na karni pade.
 * Ye kyu hai: Phase 3.4 brief ka Step 7/16.
 * Dusre components se kaise communicate karta hai: BatchEmbeddingResponse.
 * embeddings ke andar nested hai; EmbeddingServiceImpl ise banata hai.
 */
public record BatchEmbeddingItem(
        int index,
        List<Float> embedding,
        String errorCode,
        String errorMessage
) {
}
