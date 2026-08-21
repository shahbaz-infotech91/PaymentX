package com.paymentx.prompt.entity;

/**
 * English:
 * The kind of prompt a template represents, matching the four
 * consumers named in PAYMENTX_PHASE_3_ARCHITECTURE.md §4/§5: SYSTEM
 * (system-level instructions for AI Chat / Agent Orchestrator), USER
 * (user-facing conversational templates), TOOL (MCP tool-call/tool-result
 * framing for Agent Orchestrator), RAG (retrieval-context assembly
 * templates for RAG Service). This is metadata only - Prompt Service
 * does not behave differently per type today, it exists so a future
 * consumer can filter/discover prompts by role without guessing from
 * the key string alone.
 * Why it exists: PromptTemplate.type - Step 3/12 of the Phase 3.2 brief.
 * How it communicates with other components: serialized as a plain
 * string in every PromptResponse/PromptVersionResponse JSON body.
 *
 * Hinglish:
 * Ek template kis tarah ka prompt represent karta hai, PAYMENTX_PHASE_3_
 * ARCHITECTURE.md §4/§5 me named 4 consumers se match karte hue: SYSTEM
 * (AI Chat / Agent Orchestrator ke liye system-level instructions), USER
 * (user-facing conversational templates), TOOL (Agent Orchestrator ke
 * liye MCP tool-call/tool-result framing), RAG (RAG Service ke liye
 * retrieval-context assembly templates). Ye sirf metadata hai - Prompt
 * Service aaj type ke hisaab se alag behave nahi karta, ye isliye exist
 * karta hai taaki ek future consumer sirf key string se guess kiye bina
 * role ke hisaab se prompts filter/discover kar sake.
 * Ye kyu hai: PromptTemplate.type - Phase 3.2 brief ka Step 3/12.
 * Dusre components se kaise communicate karta hai: har PromptResponse/
 * PromptVersionResponse JSON body me ek plain string ke roop me
 * serialize hota hai.
 */
public enum PromptType {
    SYSTEM,
    USER,
    TOOL,
    RAG
}
