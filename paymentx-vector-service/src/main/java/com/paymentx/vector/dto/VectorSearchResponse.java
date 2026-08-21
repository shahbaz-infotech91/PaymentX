package com.paymentx.vector.dto;

import java.util.List;

/**
 * English:
 * The real result of one top-K similarity search - `results` is empty
 * (never fabricated/padded) when no chunk matches the filters/threshold
 * (Step 35 item 18 - "empty result" is a real, valid, honestly-reported
 * outcome, not an error). `resultCount` mirrors `results.size()`
 * explicitly so a caller/log line does not need to re-derive it.
 * Why it exists: Step 13/14's exact search-response shape.
 * How it communicates with other components: built by
 * VectorStoreServiceImpl.search; returned inside
 * ApiResponse&lt;VectorSearchResponse&gt; by VectorController.search -
 * this IS the contract a future RAG Service will call and consume
 * (Step 28 - Vector Database provides the API, does not orchestrate
 * RAG itself).
 *
 * Hinglish:
 * Ek top-K similarity search ka real result - `results` khali hota hai
 * (kabhi fabricated/padded nahi) jab koi chunk filters/threshold match
 * nahi karta (Step 35 item 18 - "empty result" ek real, valid, honestly-
 * reported outcome hai, ek error nahi). `resultCount` explicitly
 * `results.size()` mirror karta hai taaki ek caller/log line use dobara
 * derive na karna pade.
 * Ye kyu hai: Step 13/14 ka exact search-response shape.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl.
 * search ise banata hai; VectorController.search ise
 * ApiResponse&lt;VectorSearchResponse&gt; ke andar return karta hai -
 * yehi wo contract hai jise ek future RAG Service call aur consume
 * karegi (Step 28 - Vector Database API deta hai, khud RAG orchestrate
 * nahi karta).
 */
public record VectorSearchResponse(
        List<VectorSearchResultItem> results,
        int resultCount,
        String provider,
        String model
) {
}
