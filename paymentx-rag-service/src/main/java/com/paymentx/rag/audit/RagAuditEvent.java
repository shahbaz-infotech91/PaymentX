package com.paymentx.rag.audit;

import java.util.List;

/**
 * English:
 * The minimum safe metadata Phase 3.10.2 requires for one RAG retrieval operation - never the user's
 * `query` text, never the LLM's `answer`, never full chunk/document content. `chunkIds` and
 * `similarityScores` are the real, already-existing `RetrievedChunk.chunkId()`/`RetrievedChunk.score()`
 * values Vector Service returned (same index order, so `chunkIds.get(i)` and `similarityScores.get(i)`
 * describe the same chunk) - never recalculated, never a fabricated identifier. `requestId` is a fresh
 * UUID generated once per `RagServiceImpl.query()` call, the same "one UUID per operation, distinct from
 * `correlationId`" convention `AgentOrchestratorService`/`AgentExecution` already establish elsewhere in
 * this platform - RAG Service had no per-query identifier of its own before this phase. `latencyMs`
 * specifically means the RETRIEVAL step's own elapsed time (query validation + embedding + vector search),
 * not the full end-to-end request (which also includes context construction, prompt rendering, and LLM
 * generation, already tracked separately as `RagQueryMetadata.totalLatencyMs`) - see `RagAuditClient`'s
 * javadoc and PAYMENTX_PHASE_3_10_2_RAG_AUDIT.md §7 for why this distinction matters. `status` is one of
 * `RagAuditClient.STATUS_SUCCESS`/`STATUS_NO_RESULTS`/`STATUS_FAILURE` - reusing this service's own
 * existing SUCCESS/INSUFFICIENT_CONTEXT vocabulary (`RagQueryStatus`) in spirit, scoped to the retrieval
 * step alone (a later, post-retrieval LLM refusal is not a retrieval failure and is never reported as one
 * here - see `RagServiceImpl`'s integration point).
 * Why it exists: Phase 3.10.2's approved RAG Service audit layer requirement.
 * How it communicates with other components: built by `RagServiceImpl.query()`; consumed by
 * `RagAuditClient.recordRetrieval`.
 *
 * Hinglish:
 * Phase 3.10.2 ko ek real RAG retrieval operation ke liye chahiye wala minimum safe metadata - kabhi
 * user ka `query` text nahi, kabhi LLM ka `answer` nahi, kabhi full chunk/document content nahi.
 * `chunkIds` aur `similarityScores` real, already-existing `RetrievedChunk.chunkId()`/
 * `RetrievedChunk.score()` values hain jo Vector Service ne return kiye (same index order me, taaki
 * `chunkIds.get(i)` aur `similarityScores.get(i)` usi chunk ko describe karein) - kabhi recalculate nahi
 * kiye gaye, kabhi ek fabricated identifier nahi. `requestId` ek fresh UUID hai jo har
 * `RagServiceImpl.query()` call par ek baar generate hota hai, wahi "har operation ke liye ek UUID,
 * `correlationId` se alag" convention jo `AgentOrchestratorService`/`AgentExecution` is platform me
 * kahin aur already establish kar chuke hain - RAG Service ke paas is phase se pehle apna koi per-query
 * identifier nahi tha. `latencyMs` specifically RETRIEVAL step ka apna elapsed time matlab rakhta hai
 * (query validation + embedding + vector search), poora end-to-end request nahi (jisme context
 * construction, prompt rendering, aur LLM generation bhi shamil hain, already alag se track hote hain
 * `RagQueryMetadata.totalLatencyMs` ke roop me). `status` `RagAuditClient.STATUS_SUCCESS`/
 * `STATUS_NO_RESULTS`/`STATUS_FAILURE` me se ek hai - is service ke apne existing SUCCESS/
 * INSUFFICIENT_CONTEXT vocabulary (`RagQueryStatus`) ko spirit me reuse karte hue, sirf retrieval step
 * tak scoped (ek baad ki, post-retrieval LLM refusal ek retrieval failure nahi hai aur yahan kabhi aise
 * report nahi hoti).
 * Ye kyu hai: Phase 3.10.2 ka approved RAG Service audit layer requirement.
 * Dusre components se kaise communicate karta hai: `RagServiceImpl.query()` ise banata hai;
 * `RagAuditClient.recordRetrieval` ise consume karta hai.
 */
public record RagAuditEvent(
        String correlationId,
        String requestId,
        int retrievalCount,
        List<String> chunkIds,
        List<Double> similarityScores,
        long latencyMs,
        String status
) {
}
