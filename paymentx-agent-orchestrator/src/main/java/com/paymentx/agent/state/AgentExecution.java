package com.paymentx.agent.state;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * English:
 * Step 2's exact explicit agent execution state - one instance per real
 * agent run, created at the start of orchestrator/
 * AgentOrchestratorService.execute(...) and discarded when the HTTP
 * response is written. Never persisted to a database (Step 21/44 - no
 * long-term memory in this phase) and never shared across requests -
 * this is intentionally the ENTIRE state model, not a subset, so there
 * is exactly one place that answers "what has this run done so far."
 * Carries `requestId`/`correlationId`/`traceId`/`userId`/`userQuery`
 * (identity/input), `currentStep` (the real AgentState machine
 * position), `iteration`/`toolCallCount` (the real counters Step 10's
 * limits are checked against), `retrievedContext`/`toolCalls` (real,
 * safe evidence - see RagRetrievalRecord's/ToolCallRecord's own
 * javadoc for why these never carry chain-of-thought or unsanitized
 * tool output), and `status`/`finalAnswer` (the real terminal outcome).
 * Deliberately has NO field for raw LLM chain-of-thought text - only
 * AgentPlan.reasoning() (a short decision label) is ever captured, and
 * only that is what ends up in toolCalls()/retrievedContext() logging
 * (Step 2's explicit "Do NOT store hidden chain-of-thought").
 * Why it exists: Step 2's exact required shape.
 * How it communicates with other components: created and mutated
 * exclusively by orchestrator/AgentOrchestratorService as its bounded
 * loop progresses; read by audit/AgentAuditClient and
 * controller/AgentController (via the final dto/AgentExecuteResponse
 * mapping) - never exposed directly over the wire itself.
 *
 * Hinglish:
 * Step 2 ka exact explicit agent execution state - ek real agent run ke
 * liye ek instance, orchestrator/AgentOrchestratorService.execute(...)
 * ke start par banaya jaata hai aur HTTP response likhe jaane par
 * discard ho jaata hai. Kabhi ek database me persist nahi hota (Step
 * 21/44 - is phase me koi long-term memory nahi) aur kabhi requests ke
 * across share nahi hota - ye jaan-boojh kar POORA state model hai, ek
 * subset nahi, taaki exactly ek jagah ho jo "is run ne ab tak kya kiya"
 * ka jawab de. `requestId`/`correlationId`/`traceId`/`userId`/
 * `userQuery` (identity/input) carry karta hai, `currentStep` (real
 * AgentState machine position), `iteration`/`toolCallCount` (real
 * counters jinke against Step 10 ki limits check hoti hain),
 * `retrievedContext`/`toolCalls` (real, safe evidence - ye kabhi
 * chain-of-thought ya unsanitized tool output kyu carry nahi karte ke
 * liye RagRetrievalRecord/ToolCallRecord ka apna javadoc dekho), aur
 * `status`/`finalAnswer` (real terminal outcome). Jaan-boojh kar raw
 * LLM chain-of-thought text ke liye koi field nahi hai - sirf
 * AgentPlan.reasoning() (ek short decision label) kabhi capture hota
 * hai, aur sirf wahi toolCalls()/retrievedContext() logging me end up
 * hota hai (Step 2 ka explicit "Hidden chain-of-thought store MAT
 * karo").
 * Ye kyu hai: Step 2 ka exact required shape.
 * Dusre components se kaise communicate karta hai: orchestrator/
 * AgentOrchestratorService dwara exclusively banaya aur mutate kiya
 * jaata hai jaise iska bounded loop progress karta hai; audit/
 * AgentAuditClient aur controller/AgentController (final
 * dto/AgentExecuteResponse mapping ke through) dwara padha jaata hai -
 * khud kabhi seedhe wire par expose nahi hota.
 */
@Getter
@Setter
public class AgentExecution {

    private final String requestId;
    private final String correlationId;
    private final String traceId;
    private final String userId;
    private final String userQuery;
    // Phase 4.1 - logical agent identity this execution is running as, resolved by
    // registry.AgentRegistry before the run starts. Deliberately distinct from userId
    // (authenticated human identity), and never a participantId/paymentId.
    private final String agentId;

    private AgentState currentStep = AgentState.RECEIVED;
    private int iteration = 0;
    private int toolCallCount = 0;

    // orchestrator/AgentOrchestratorService.execute() runs runLoop() on a background executor
    // thread and bounds it with future.get(timeout, ...); on a genuine timeout the caller thread
    // proceeds to read this execution (audit write, response building) while the background
    // thread may still be running (best-effort future.cancel(true) does not guarantee immediate
    // stop for a thread blocked in a blocking HTTP call) - a plain ArrayList's iterator throws
    // ConcurrentModificationException under that real concurrent add-while-iterate race, which
    // would otherwise cause AgentAuditClient.recordAgentRun to silently fail (its own catch-all
    // swallows the exception) and the execution to vanish from Execution History. CopyOnWriteArrayList
    // has snapshot iterators that never throw CME, closing that race without changing any
    // existing behavior for the single-threaded-read common case.
    private final List<RagRetrievalRecord> retrievedContext = new CopyOnWriteArrayList<>();
    private final List<ToolCallRecord> toolCalls = new CopyOnWriteArrayList<>();

    private AgentState status;
    private String finalAnswer;

    // Phase 4.7 - mutable (not a constructor param, to avoid touching AgentAuditClientTest's/
    // AgentPlannerTest's existing direct construction sites) rather than folded only into
    // userQuery's own "Payment reference: X. <question>" text (Phase 4.2.3's effectiveUserQuery),
    // so Control Center's AI Agent Control Center execution history can filter/display it as its
    // own structured field. Purely descriptive - never re-derives or overrides tool arguments.
    private String paymentReference;

    // Phase 5 - LLM provider/fallback visibility (LlmProviderRouter, Phase 4.8.6). Mutable, same
    // pattern as paymentReference above (avoids touching existing constructor call sites).
    // lastLlmProvider/lastFallbackReason reflect the MOST RECENT real LLM call this execution
    // made (the planning loop calls the LLM once per iteration; the last call is the one whose
    // answer - including a FINAL_RESPONSE - the caller actually sees). fallbackUsedInExecution is
    // a whole-execution OR across every call: true if ANY call during this run used the fallback
    // provider, even if a later call succeeded on primary again - an operator investigating a
    // slow/unusual execution should see that fallback was involved at all, not just at the end.
    private String lastLlmProvider;
    private boolean fallbackUsedInExecution;
    private String lastFallbackReason;

    public AgentExecution(String requestId, String correlationId, String traceId, String userId, String userQuery, String agentId) {
        this.requestId = requestId;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.userId = userId;
        this.userQuery = userQuery;
        this.agentId = agentId;
    }

    public AgentExecution(String requestId, String correlationId, String traceId, String userId, String userQuery) {
        this(requestId, correlationId, traceId, userId, userQuery, null);
    }

    public void addRagRetrieval(RagRetrievalRecord record) {
        retrievedContext.add(record);
    }

    public void addToolCall(ToolCallRecord record) {
        toolCalls.add(record);
        toolCallCount++;
    }
}
