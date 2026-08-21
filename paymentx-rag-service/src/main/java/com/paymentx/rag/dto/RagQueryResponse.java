package com.paymentx.rag.dto;

import java.util.List;

/**
 * English:
 * The real, normalized result of one RAG query - Step 6/34's exact
 * contract. `sources` is empty exactly when `status` is
 * INSUFFICIENT_CONTEXT (never fabricated, never padded - Step 22).
 * `answer` for INSUFFICIENT_CONTEXT is a real, honest, hardcoded
 * explanation ("I couldn't find enough relevant information in the
 * PaymentX knowledge base to answer this reliably.", Step 22's own
 * example) - never blank, so a caller/UI always has real text to show
 * regardless of outcome.
 * Why it exists: Step 6/34's exact response contract.
 * How it communicates with other components: built by RagServiceImpl;
 * returned inside ApiResponse&lt;RagQueryResponse&gt; by
 * RagController.query - this IS the contract Control Center's
 * AiChatService now calls (Step 35) instead of calling LLM Service
 * directly.
 *
 * Hinglish:
 * Ek RAG query ka real, normalized result - Step 6/34 ka exact
 * contract. `sources` exactly tab khali hota hai jab `status`
 * INSUFFICIENT_CONTEXT ho (kabhi fabricated nahi, kabhi padded nahi -
 * Step 22). INSUFFICIENT_CONTEXT ke liye `answer` ek real, honest,
 * hardcoded explanation hai ("I couldn't find enough relevant
 * information in the PaymentX knowledge base to answer this
 * reliably.", Step 22 ka apna example) - kabhi blank nahi, taaki ek
 * caller/UI ke paas outcome chahe kuch bhi ho hamesha real text ho
 * dikhane ke liye.
 * Ye kyu hai: Step 6/34 ka exact response contract.
 * Dusre components se kaise communicate karta hai: RagServiceImpl ise
 * banata hai; RagController.query ise ApiResponse&lt;RagQueryResponse&gt;
 * ke andar return karta hai - yehi wo contract hai jise Control Center
 * ki AiChatService ab call karti hai (Step 35) seedhe LLM Service call
 * karne ke bajaye.
 */
public record RagQueryResponse(
        String answer,
        RagQueryStatus status,
        List<RagSource> sources,
        RagQueryMetadata metadata
) {
}
