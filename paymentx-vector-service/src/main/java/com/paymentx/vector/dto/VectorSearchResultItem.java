package com.paymentx.vector.dto;

import java.util.Map;
import java.util.UUID;

/**
 * English:
 * One real search hit - deliberately does NOT include the full
 * embedding vector (Step 26 - "Search results should normally return
 * metadata/content/score, NOT the full vector" - a 1536-float array per
 * result would bloat the response for zero caller benefit; a caller
 * that genuinely needs the raw vector already has it, since it supplied
 * the query vector itself). `distance` is pgvector's real `<=>`
 * cosine-distance operator output, verbatim - mathematically in
 * [0, 2], where 0 means identical direction and 2 means opposite.
 * `score` is NOT a second, independently-computed value - it is
 * literally `1 - distance` (real cosine similarity, mathematically in
 * [-1, 1] for that operator's output range), included because "higher
 * is better" is the more common convention for a caller-facing
 * relevance number (matches the Phase 3.5 brief's own example response,
 * `"score": 0.91`) - but `distance` is kept alongside it, not hidden,
 * specifically so a caller can verify the math itself rather than
 * trusting an opaque, unlabeled "similarity" (Step 12 - "Do NOT label
 * distance as similarity unless mathematically correct").
 * Why it exists: Step 13's exact search-result shape.
 * How it communicates with other components: built by
 * VectorStoreServiceImpl.search from a real row
 * AiDocumentEmbeddingRepository's native query returned; nested inside
 * VectorSearchResponse.results.
 *
 * Hinglish:
 * Ek real search hit - jaan-boojh kar poora embedding vector include
 * NAHI karta (Step 26 - "Search results normally metadata/content/score
 * return karne chahiye, poora vector NAHI" - ek result me ek 1536-float
 * array response ko zero caller benefit ke liye bloat kar deta; jis
 * caller ko genuinely raw vector chahiye uske paas already hai, kyunki
 * usne khud query vector supply kiya tha). `distance` pgvector ka real
 * `<=>` cosine-distance operator output hai, verbatim - mathematically
 * [0, 2] me, jahan 0 ka matlab identical direction aur 2 ka matlab
 * opposite hai. `score` ek doosri, independently-computed value NAHI
 * hai - ye literally `1 - distance` hai (real cosine similarity,
 * mathematically [-1, 1] me us operator ke output range ke liye),
 * include kiya gaya hai kyunki "higher is better" ek caller-facing
 * relevance number ke liye zyada common convention hai (Phase 3.5
 * brief ke apne example response se match karta hai, `"score": 0.91`)
 * - lekin `distance` bhi saath rakha gaya hai, chhupaya nahi gaya,
 * specifically taaki ek caller khud math verify kar sake, ek opaque,
 * unlabeled "similarity" par trust karne ke bajaye (Step 12 - "distance
 * ko similarity label mat karo jab tak mathematically correct na ho").
 * Ye kyu hai: Step 13 ka exact search-result shape.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl.
 * search ise ek real row se banata hai jo
 * AiDocumentEmbeddingRepository ka native query return karta hai;
 * VectorSearchResponse.results ke andar nested hai.
 */
public record VectorSearchResultItem(
        UUID documentId,
        String documentKey,
        UUID chunkId,
        int chunkIndex,
        String content,
        double score,
        double distance,
        String provider,
        String model,
        Map<String, Object> metadata
) {
}
