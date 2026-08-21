package com.paymentx.rag.dto;

/**
 * English:
 * Real retrieval/latency diagnostics about how one RAG answer was
 * produced - `retrievedChunks` is Vector Service's raw top-K count
 * (before any threshold/dedup/limit is applied); `contextChunksUsed` is
 * how many of those actually survived into the final prompt
 * ContextBuilder built; `rejectedByThreshold` is exactly
 * `retrievedChunks` minus the count that passed `rag.min-score` (Step
 * 12 - this is the same real number rag_relevance_threshold_rejections
 * records, just visible per-request too, not only in aggregate
 * metrics). `totalLatencyMs` is the real wall-clock time across every
 * downstream call this request made (Step 26's latency-visibility
 * requirement, exposed per-request in addition to the aggregate
 * Micrometer timers).
 * Why it exists: Step 6/34's "metadata": {"retrievedChunks": 3} example
 * - real, inspectable retrieval diagnostics, not decoration.
 * How it communicates with other components: built by RagServiceImpl;
 * nested inside RagQueryResponse.metadata.
 *
 * Hinglish:
 * Ek RAG answer kaise produce hua uske baare me real retrieval/latency
 * diagnostics - `retrievedChunks` Vector Service ka raw top-K count hai
 * (koi threshold/dedup/limit apply hone se pehle); `contextChunksUsed`
 * batata hai un me se kitne actually final prompt me survive hue jo
 * ContextBuilder ne banaya; `rejectedByThreshold` exactly
 * `retrievedChunks` minus wo count hai jo `rag.min-score` pass hui
 * (Step 12 - ye wahi real number hai jo rag_relevance_threshold_rejections
 * record karta hai, sirf per-request bhi visible hai, sirf aggregate
 * metrics me nahi). `totalLatencyMs` is request ne jo bhi downstream
 * call kiye unke across ka real wall-clock time hai (Step 26 ki
 * latency-visibility requirement, per-request bhi expose kiya gaya
 * aggregate Micrometer timers ke alawa).
 * Ye kyu hai: Step 6/34 ka "metadata": {"retrievedChunks": 3} example -
 * real, inspectable retrieval diagnostics, decoration nahi.
 * Dusre components se kaise communicate karta hai: RagServiceImpl ise
 * banata hai; RagQueryResponse.metadata ke andar nested hai.
 */
public record RagQueryMetadata(
        int retrievedChunks,
        int contextChunksUsed,
        int rejectedByThreshold,
        long totalLatencyMs
) {
}
