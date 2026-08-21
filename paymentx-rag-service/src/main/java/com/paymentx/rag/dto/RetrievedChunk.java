package com.paymentx.rag.dto;

/**
 * English:
 * The internal, full-fidelity representation of one Vector Service
 * search hit as it flows through this service - VectorServiceClient
 * builds it from Vector Service's real JSON response, ContextBuilder
 * consumes `content` to build the prompt context, and RagServiceImpl
 * later trims it down into the much narrower, public RagSource (no
 * `content`, no raw score-vs-distance ambiguity exposed) for the API
 * response. Kept as a separate type from RagSource specifically because
 * the two have different audiences and different completeness needs -
 * ContextBuilder needs `content`, the HTTP response never should (Step
 * 21).
 * Why it exists: keeps VectorServiceClient's parsing logic decoupled
 * from the two different downstream consumers (ContextBuilder,
 * RagServiceImpl's response-mapping) that each need a different subset
 * of the same real search hit.
 * How it communicates with other components: built by
 * VectorServiceClient.search from Vector Service's real response;
 * consumed by ContextBuilder and RagServiceImpl.
 *
 * Hinglish:
 * Ek Vector Service search hit ka internal, full-fidelity
 * representation jaise ye is service se hoke guzarta hai -
 * VectorServiceClient ise Vector Service ke real JSON response se
 * banata hai, ContextBuilder `content` consume karta hai prompt context
 * banane ke liye, aur RagServiceImpl baad me ise trim karke ek kaafi
 * narrower, public RagSource banata hai (koi `content` nahi, koi raw
 * score-vs-distance ambiguity expose nahi) API response ke liye.
 * RagSource se ek alag type ke roop me specifically rakha gaya hai
 * kyunki dono ke audience aur completeness needs alag hain -
 * ContextBuilder ko `content` chahiye, HTTP response ko kabhi nahi
 * chahiye (Step 21).
 * Ye kyu hai: VectorServiceClient ki parsing logic ko un do alag
 * downstream consumers (ContextBuilder, RagServiceImpl ka response-
 * mapping) se decoupled rakhta hai jinhe har ek ko usi real search hit
 * ka ek alag subset chahiye.
 * Dusre components se kaise communicate karta hai:
 * VectorServiceClient.search ise Vector Service ke real response se
 * banata hai; ContextBuilder aur RagServiceImpl ise consume karte hain.
 */
public record RetrievedChunk(
        String documentId,
        String chunkId,
        String documentKey,
        String content,
        double score,
        double distance
) {
}
