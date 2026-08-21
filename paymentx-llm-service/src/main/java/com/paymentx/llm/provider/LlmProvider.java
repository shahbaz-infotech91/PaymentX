package com.paymentx.llm.provider;

/**
 * English:
 * The ProviderAdapter abstraction PAYMENTX_PHASE_3_ARCHITECTURE.md §8
 * calls for: LlmService -> LlmProvider -> ProviderAdapter, so
 * LlmServiceImpl (the application layer) never touches a
 * provider-specific type. Exactly one implementation exists in Phase
 * 3.3 (AnthropicLlmProvider, the one real provider this phase chose per
 * Step 6/26 of the brief) but every call site depends on this
 * interface, not that class - adding a second provider later means
 * writing a second implementation and a selection strategy, not
 * touching LlmServiceImpl, LlmController, or any DTO.
 * Why it exists: Step 26 - "maintain a provider abstraction... choose
 * ONE real provider now but architect for more later."
 * How it communicates with other components: injected into
 * LlmServiceImpl as a plain Spring bean (single implementation on the
 * classpath today, so no @Qualifier/selection logic is needed yet -
 * that selection strategy is explicitly future-phase scope, not
 * invented speculatively here).
 *
 * Hinglish:
 * PAYMENTX_PHASE_3_ARCHITECTURE.md §8 jo ProviderAdapter abstraction
 * maangta hai wahi: LlmService -> LlmProvider -> ProviderAdapter, taaki
 * LlmServiceImpl (application layer) kabhi ek provider-specific type na
 * chhue. Phase 3.3 me exactly ek implementation exist karta hai
 * (AnthropicLlmProvider, wo ek real provider jo is phase ne brief ke
 * Step 6/26 ke hisaab se choose kiya) lekin har call site is interface
 * par depend karta hai, us class par nahi - baad me ek doosra provider
 * add karne ka matlab hoga ek doosri implementation aur ek selection
 * strategy likhna, LlmServiceImpl, LlmController, ya kisi DTO ko na
 * chhuna.
 * Ye kyu hai: Step 26 - "provider abstraction maintain karo... abhi EK
 * real provider choose karo lekin zyada ke liye architect karo."
 * Dusre components se kaise communicate karta hai: LlmServiceImpl me ek
 * plain Spring bean ke roop me inject hota hai (aaj classpath par ek hi
 * implementation hai, isliye abhi koi @Qualifier/selection logic ki
 * zaroorat nahi - wo selection strategy explicitly future-phase scope
 * hai, yahan speculatively invent nahi ki gayi).
 */
public interface LlmProvider {

    String providerName();

    LlmProviderResult generate(LlmProviderRequest request);
}
