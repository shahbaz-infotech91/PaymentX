package com.paymentx.agent.dto;

/**
 * English:
 * Safe, real execution telemetry about how an agent run reached its
 * answer - iteration count, tool call count, whether RAG was consulted,
 * and total latency. Deliberately contains nothing else - no chain-of-
 * thought, no rendered system prompt, no raw LLM plan JSON, no internal
 * service URLs (Step 14 - "Do NOT expose: chain-of-thought, internal
 * prompts, system instructions, API keys, tool credentials, database
 * information, internal stack traces").
 * Why it exists: Step 14's "optional execution metadata" allowance.
 * How it communicates with other components: built by
 * orchestrator/AgentOrchestratorService from the final
 * state/AgentExecution; returned inside
 * dto/AgentExecuteResponse.executionMetadata().
 *
 * Hinglish:
 * Ek agent run apne answer tak kaise pahuncha iske baare me safe, real
 * execution telemetry - iteration count, tool call count, RAG consult
 * hui ya nahi, aur total latency. Jaan-boojh kar aur kuch nahi rakhta -
 * na chain-of-thought, na rendered system prompt, na raw LLM plan JSON,
 * na internal service URLs (Step 14 - "Expose MAT karo: chain-of-
 * thought, internal prompts, system instructions, API keys, tool
 * credentials, database information, internal stack traces").
 * Ye kyu hai: Step 14 ka "optional execution metadata" allowance.
 * Dusre components se kaise communicate karta hai:
 * orchestrator/AgentOrchestratorService dwara final state/AgentExecution
 * se banaya jaata hai; dto/AgentExecuteResponse.executionMetadata() ke
 * andar return hota hai.
 */
public record ExecutionMetadata(
        int iterations,
        int toolCallCount,
        boolean ragUsed,
        long totalLatencyMs
) {
}
