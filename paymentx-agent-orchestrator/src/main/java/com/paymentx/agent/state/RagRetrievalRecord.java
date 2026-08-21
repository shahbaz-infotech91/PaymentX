package com.paymentx.agent.state;

import java.util.List;

/**
 * English:
 * One real RAG Service query's outcome, recorded into
 * state/AgentExecution.retrievedContext() for the duration of a single
 * agent run. `status`/`answer`/`sourceLabels` are RAG Service's own
 * real, honest values (RagQueryStatus - SUCCESS/INSUFFICIENT_CONTEXT/
 * REFUSED - passed through verbatim, never reinterpreted here, matching
 * the exact precedent Control Center's AiChatService already set when
 * it started calling RAG Service in Phase 3.6).
 * Why it exists: Step 2's `retrievedContext`, Step 15's RAG Evidence
 * requirement.
 * How it communicates with other components: appended to
 * state/AgentExecution by orchestrator/AgentOrchestratorService after
 * every real RAG Service call; surfaced in dto/SourceEvidence in the
 * final response.
 *
 * Hinglish:
 * Ek real RAG Service query ka outcome, state/AgentExecution.retrievedContext()
 * me record hota hai ek single agent run ki duration ke liye.
 * `status`/`answer`/`sourceLabels` RAG Service ki apni real, honest
 * values hain (RagQueryStatus - SUCCESS/INSUFFICIENT_CONTEXT/REFUSED -
 * verbatim pass through, yahan kabhi reinterpret nahi hoti, exactly wahi
 * precedent match karte hue jo Control Center ka AiChatService already
 * set kar chuka hai jab usne Phase 3.6 me RAG Service call karna shuru
 * kiya).
 * Ye kyu hai: Step 2 ka `retrievedContext`, Step 15 ka RAG Evidence
 * requirement.
 * Dusre components se kaise communicate karta hai:
 * orchestrator/AgentOrchestratorService dwara har real RAG Service call
 * ke baad state/AgentExecution me append hota hai; final response me
 * dto/SourceEvidence me surface hota hai.
 */
public record RagRetrievalRecord(
        String query,
        String status,
        String answer,
        List<String> sourceLabels
) {
}
