package com.paymentx.agent.dto;

/**
 * English:
 * One real RAG Service source citation, carried into the final agent
 * response - mirrors paymentx-rag-service's own RagSource(documentId,
 * chunkId, source, score) field-for-field, so this is a real, verbatim
 * passthrough of RAG Service's own already-honest evidence, never
 * re-derived or invented (Step 15 - "the final response should be
 * traceable").
 * Why it exists: Step 14/15/39.
 * How it communicates with other components: built by
 * orchestrator/AgentOrchestratorService from client/RagServiceClient's
 * parsed response; returned inside dto/AgentExecuteResponse.sources().
 *
 * Hinglish:
 * Ek real RAG Service source citation, final agent response me carry
 * hota hai - paymentx-rag-service ke apne RagSource(documentId, chunkId,
 * source, score) ko field-for-field mirror karta hai, isliye ye RAG
 * Service ke apne already-honest evidence ka ek real, verbatim
 * passthrough hai, kabhi re-derive ya invent nahi hota (Step 15 -
 * "final response traceable hona chahiye").
 * Ye kyu hai: Step 14/15/39.
 * Dusre components se kaise communicate karta hai:
 * orchestrator/AgentOrchestratorService dwara client/RagServiceClient
 * ke parsed response se banaya jaata hai; dto/AgentExecuteResponse.sources()
 * ke andar return hota hai.
 */
public record SourceEvidence(
        String documentId,
        String chunkId,
        String source,
        double score
) {
}
