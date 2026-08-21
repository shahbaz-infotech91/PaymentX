package com.paymentx.embedding.service;

import com.paymentx.embedding.dto.BatchEmbeddingRequest;
import com.paymentx.embedding.dto.BatchEmbeddingResponse;
import com.paymentx.embedding.dto.EmbeddingHealthResponse;
import com.paymentx.embedding.dto.EmbeddingRequest;
import com.paymentx.embedding.dto.EmbeddingResponse;

/**
 * English:
 * The application-layer contract EmbeddingController depends on -
 * matches LLM Service's LlmService/Prompt Service's PromptService
 * interface/impl split exactly: a thin HTTP adapter (EmbeddingController)
 * that never contains business logic, and one real implementation
 * (EmbeddingServiceImpl) that never imports Spring MVC types.
 * Why it exists: keeps provider-selection/validation/metrics logic
 * unit-testable without standing up a servlet container.
 * How it communicates with other components: implemented by
 * EmbeddingServiceImpl; injected into EmbeddingController.
 *
 * Hinglish:
 * Wo application-layer contract jispar EmbeddingController depend karta
 * hai - LLM Service ke LlmService/Prompt Service ke PromptService
 * interface/impl split se exactly match karta hai: ek thin HTTP adapter
 * (EmbeddingController) jisme kabhi business logic nahi hoti, aur ek
 * real implementation (EmbeddingServiceImpl) jo kabhi Spring MVC types
 * import nahi karti.
 * Ye kyu hai: provider-selection/validation/metrics logic ko ek servlet
 * container khade kiye bina unit-testable rakhta hai.
 * Dusre components se kaise communicate karta hai: EmbeddingServiceImpl
 * dwara implement hota hai; EmbeddingController me inject hota hai.
 */
public interface EmbeddingService {

    EmbeddingResponse embed(EmbeddingRequest request);

    BatchEmbeddingResponse embedBatch(BatchEmbeddingRequest request);

    EmbeddingHealthResponse health();
}
