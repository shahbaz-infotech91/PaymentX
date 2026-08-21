package com.paymentx.agent.dto;

/**
 * English:
 * Every real, honest terminal outcome an agent run can end in - never
 * collapsed into one generic "it worked" flag, matching the exact "no
 * fake AI" precedent every prior AI Platform phase already established
 * (RagQueryStatus, Phase 3.6; the isError/errorCode shape of MCP
 * Gateway's CallToolResult, Phase 3.7). `SUCCESS` is a real, grounded
 * final answer. `INSUFFICIENT_CONTEXT` means RAG was consulted and had
 * nothing relevant (mirrors RagQueryStatus.INSUFFICIENT_CONTEXT
 * verbatim). `REFUSED` means the LLM itself declined to answer with
 * real evidence in hand. `DENIED` means the plan requested a tool this
 * agent's policy will never allow (Step 9/30) - the tool was NEVER
 * invoked. `MAX_ITERATIONS` means the bounded loop (Step 10/11)
 * exhausted its budget without reaching FINAL_RESPONSE - a safe stop,
 * not a hang. `TIMEOUT` means the overall execution deadline (Step 33)
 * was hit. `FAILED` covers any real infrastructure failure (RAG/MCP/
 * Prompt/LLM Service unavailable, a malformed LLM plan that could not
 * be safely parsed/validated).
 * Why it exists: Step 3's failure-state list + Step 39's response-
 * contract compatibility requirement.
 * How it communicates with other components: dto/AgentExecuteResponse.status();
 * mapped, unchanged, into Control Center's AiChatResponse.status by
 * AiChatService (mirroring RAG Service's own passthrough precedent).
 *
 * Hinglish:
 * Har real, honest terminal outcome jisme ek agent run khatam ho sakta
 * hai - kabhi ek generic "kaam ho gaya" flag me collapse nahi hota,
 * exactly wahi "no fake AI" precedent match karte hue jo har pichla AI
 * Platform phase already establish kar chuka hai (RagQueryStatus, Phase
 * 3.6; MCP Gateway ke CallToolResult ka isError/errorCode shape, Phase
 * 3.7). `SUCCESS` ek real, grounded final answer hai.
 * `INSUFFICIENT_CONTEXT` ka matlab hai RAG consult hui aur usme kuch
 * relevant nahi mila (RagQueryStatus.INSUFFICIENT_CONTEXT ko verbatim
 * mirror karta hai). `REFUSED` ka matlab hai LLM ne khud real evidence
 * haath me hote hue bhi jawab dene se mana kar diya. `DENIED` ka matlab
 * hai plan ne ek aisa tool maanga jise is agent ki policy kabhi allow
 * nahi karegi (Step 9/30) - tool kabhi invoke hi nahi hua.
 * `MAX_ITERATIONS` ka matlab hai bounded loop (Step 10/11) apna budget
 * FINAL_RESPONSE tak pahunche bina exhaust kar gaya - ek safe stop, ek
 * hang nahi. `TIMEOUT` ka matlab hai overall execution deadline (Step
 * 33) hit ho gayi. `FAILED` kisi bhi real infrastructure failure ko
 * cover karta hai (RAG/MCP/Prompt/LLM Service unavailable, ek malformed
 * LLM plan jise safely parse/validate nahi kiya ja saka).
 * Ye kyu hai: Step 3 ki failure-state list + Step 39 ka response-
 * contract compatibility requirement.
 * Dusre components se kaise communicate karta hai:
 * dto/AgentExecuteResponse.status(); Control Center ke AiChatResponse.status
 * me AiChatService dwara unchanged map hota hai (RAG Service ke apne
 * passthrough precedent ko mirror karte hue).
 */
public enum AgentResponseStatus {
    SUCCESS,
    INSUFFICIENT_CONTEXT,
    REFUSED,
    DENIED,
    MAX_ITERATIONS,
    TIMEOUT,
    FAILED
}
