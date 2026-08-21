package com.paymentx.controlcenter.dto.ai;

/**
 * ENGLISH: Who authored one AiChatResponse message. What it does:
 * mirrors the conventional LLM chat-message role vocabulary
 * (user/assistant/system) as a real Java enum instead of a free-text
 * String, so a typo can never produce an invalid role value. Why it
 * exists: AiChatResponse.role's type - defined now, in Phase 3.1,
 * because the API contract (Step 6/18 of the Phase 3.1 brief) must be
 * stable before LLM Service (Phase 3.3) exists to actually populate an
 * ASSISTANT-authored response. How it will communicate with the
 * backend: serialized by Jackson as a plain string ("USER"/"ASSISTANT"/
 * "SYSTEM") inside AiChatResponse's JSON body.
 *
 * HINGLISH: Ek AiChatResponse message kisne likha - ye value batata
 * hai. Ye kya karti hai: conventional LLM chat-message role vocabulary
 * (user/assistant/system) ko ek real Java enum ke roop me mirror karta
 * hai, free-text String ke bajaye, taaki ek typo kabhi ek invalid role
 * value produce na kar sake. Ye dashboard me kyu hai: AiChatResponse.role
 * ka type hai - abhi, Phase 3.1 me hi define kiya gaya hai, kyunki API
 * contract (Phase 3.1 brief ka Step 6/18) LLM Service (Phase 3.3) exist
 * karne se pehle hi stable hona chahiye taaki wo ek real ASSISTANT-
 * authored response populate kar sake. Backend se kaise connect hogi:
 * Jackson isse ek plain string ("USER"/"ASSISTANT"/"SYSTEM") ke roop me
 * AiChatResponse ke JSON body ke andar serialize karta hai.
 */
public enum AiRole {
    USER,
    ASSISTANT,
    SYSTEM
}
