package com.paymentx.agent.state;

/**
 * English:
 * Step 3's exact explicit state machine. `RECEIVED` is the initial
 * state before any planning happens. `CLASSIFYING` labels the very
 * first planning/decision call (the LLM's structured decision doubles
 * as task classification - see planning/AgentPlanner's javadoc for why
 * a separate hardcoded classifier is not needed); every subsequent
 * loop-iteration decision call is labeled `PLANNING`. `RETRIEVING`
 * covers a RAG Service call; `TOOL_EXECUTION` covers an MCP tool call;
 * `EVALUATING` is entered after either completes, before the next
 * decision; `GENERATING` is entered only once the plan's action is
 * FINAL_RESPONSE. `COMPLETED` is the one successful terminal state.
 * `FAILED`/`TIMEOUT`/`DENIED`/`REFUSED`/`INSUFFICIENT_CONTEXT`/`MAX_ITERATIONS`
 * are the six honest failure/boundary terminal states (Step 3's five,
 * plus `REFUSED` - mirroring RagQueryStatus.REFUSED's own precedent for
 * "the LLM itself declined," a distinct failure mode from a generic
 * FAILED) - never silently mapped onto COMPLETED.
 * Why it exists: Step 3's explicit requirement - "implement an explicit
 * state machine... do not implement an uncontrolled recursive loop."
 * How it communicates with other components: state/AgentExecution's
 * currentStep field; orchestrator/AgentOrchestratorService transitions
 * it at each stage of the bounded loop; audit/AgentAuditClient records
 * it per step.
 *
 * Hinglish:
 * Step 3 ka exact explicit state machine. `RECEIVED` initial state hai
 * kisi bhi planning se pehle. `CLASSIFYING` sabse pehli planning/
 * decision call ko label karta hai (LLM ka structured decision hi task
 * classification double karta hai - ek alag hardcoded classifier kyu
 * nahi chahiye ke liye planning/AgentPlanner ka javadoc dekho); har
 * agla loop-iteration decision call `PLANNING` label hota hai.
 * `RETRIEVING` ek RAG Service call cover karta hai; `TOOL_EXECUTION` ek
 * MCP tool call cover karta hai; `EVALUATING` dono me se koi bhi
 * complete hone ke baad enter hota hai, agli decision se pehle;
 * `GENERATING` sirf tabhi enter hota hai jab plan ka action
 * FINAL_RESPONSE ho. `COMPLETED` ek hi successful terminal state hai.
 * `FAILED`/`TIMEOUT`/`DENIED`/`INSUFFICIENT_CONTEXT`/`MAX_ITERATIONS`
 * paanch honest failure/boundary terminal states hain (Step 3) - kabhi
 * silently COMPLETED par map nahi hote.
 * Ye kyu hai: Step 3 ka explicit requirement - "ek explicit state
 * machine implement karo... ek uncontrolled recursive loop implement
 * mat karo."
 * Dusre components se kaise communicate karta hai: state/
 * AgentExecution ka currentStep field; orchestrator/
 * AgentOrchestratorService bounded loop ke har stage par ise transition
 * karta hai; audit/AgentAuditClient har step par ise record karta hai.
 */
public enum AgentState {
    RECEIVED,
    CLASSIFYING,
    RETRIEVING,
    PLANNING,
    TOOL_EXECUTION,
    EVALUATING,
    GENERATING,
    COMPLETED,
    FAILED,
    TIMEOUT,
    DENIED,
    REFUSED,
    INSUFFICIENT_CONTEXT,
    MAX_ITERATIONS
}
