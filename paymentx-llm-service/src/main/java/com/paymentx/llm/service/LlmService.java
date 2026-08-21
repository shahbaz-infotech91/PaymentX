package com.paymentx.llm.service;

import com.paymentx.llm.dto.GenerateRequest;
import com.paymentx.llm.dto.GenerateResponse;
import com.paymentx.llm.dto.LlmHealthResponse;

/**
 * English:
 * The application-layer contract LlmController depends on - matches
 * Prompt Service's PromptService interface/PromptServiceImpl split
 * exactly, for the same reason: a thin HTTP adapter (LlmController)
 * that never contains business logic, and one real implementation
 * (LlmServiceImpl) that never imports Spring MVC types.
 * Why it exists: keeps the provider-selection/validation/metrics logic
 * unit-testable without standing up a servlet container, and keeps this
 * module ready for a second implementation if a future phase needs one
 * (e.g. a caching decorator) without touching LlmController.
 * How it communicates with other components: implemented by
 * LlmServiceImpl; injected into LlmController.
 *
 * Hinglish:
 * Wo application-layer contract jispar LlmController depend karta hai -
 * Prompt Service ke PromptService interface/PromptServiceImpl split se
 * exactly match karta hai, usi reason se: ek thin HTTP adapter
 * (LlmController) jisme kabhi business logic nahi hoti, aur ek real
 * implementation (LlmServiceImpl) jo kabhi Spring MVC types import nahi
 * karti.
 * Ye kyu hai: provider-selection/validation/metrics logic ko ek servlet
 * container khade kiye bina unit-testable rakhta hai, aur is module ko
 * ek future doosri implementation ke liye ready rakhta hai (jaise ek
 * caching decorator) bina LlmController chhue.
 * Dusre components se kaise communicate karta hai: LlmServiceImpl dwara
 * implement hota hai; LlmController me inject hota hai.
 */
public interface LlmService {

    GenerateResponse generate(GenerateRequest request);

    LlmHealthResponse health();
}
