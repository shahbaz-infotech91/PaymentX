package com.paymentx.rag.service;

import com.paymentx.rag.dto.RagHealthResponse;
import com.paymentx.rag.dto.RagQueryRequest;
import com.paymentx.rag.dto.RagQueryResponse;

/**
 * English:
 * The application-layer contract RagController depends on - matches
 * every other AI Platform service's ThingService interface/impl split
 * exactly: a thin HTTP adapter (RagController) that never contains
 * orchestration logic, and one real implementation (RagServiceImpl)
 * that owns the full retrieve-then-generate pipeline.
 * Why it exists: keeps the orchestration logic unit-testable
 * independent of the HTTP layer.
 * How it communicates with other components: implemented by
 * RagServiceImpl; injected into RagController.
 *
 * Hinglish:
 * Wo application-layer contract jispar RagController depend karta hai -
 * har doosri AI Platform service ke ThingService interface/impl split
 * se exactly match karta hai: ek thin HTTP adapter (RagController) jisme
 * kabhi orchestration logic nahi hoti, aur ek real implementation
 * (RagServiceImpl) jo poore retrieve-then-generate pipeline ki malik
 * hai.
 * Ye kyu hai: orchestration logic ko HTTP layer se independent unit-
 * testable rakhta hai.
 * Dusre components se kaise communicate karta hai: RagServiceImpl dwara
 * implement hota hai; RagController me inject hota hai.
 */
public interface RagService {

    RagQueryResponse query(RagQueryRequest request);

    RagHealthResponse health();
}
