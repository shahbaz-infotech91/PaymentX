package com.paymentx.agent.state;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the TIMEOUT audit/history investigation: AgentOrchestratorService.execute()
 * bounds runLoop() with future.get(timeout, ...), but on a genuine TimeoutException the
 * background executor thread running runLoop() is not guaranteed to have stopped yet (best-effort
 * future.cancel(true) does not immediately interrupt a thread blocked in a plain blocking HTTP
 * call) - it can keep mutating this same AgentExecution's toolCalls/retrievedContext while the
 * timeout-handling thread concurrently reads them to build the audit payload
 * (AgentAuditClient.recordAgentRun) and the HTTP response (buildResponse). A plain ArrayList's
 * iterator throws ConcurrentModificationException under that real concurrent add-while-iterate
 * race; AgentAuditClient's own catch-all then silently swallows it, and the whole audit event for
 * that execution is lost - the execution vanishes from Execution History with no error surfaced
 * anywhere. This proves the two fields are safe under exactly that race.
 */
class AgentExecutionThreadSafetyTest {

    @RepeatedTest(5)
    void toolCallsAndRetrievedContext_neverThrowConcurrentModification_underConcurrentAddAndIterate() throws InterruptedException {
        AgentExecution execution = new AgentExecution("req-1", "corr-1", "trace-1", "user-1", "query", "agent-1");
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicReference<Throwable> mutatorFailure = new AtomicReference<>();
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();

        // Mimics runLoop() continuing to append real evidence after a timeout was already declared.
        Thread mutator = new Thread(() -> {
            try {
                for (int i = 0; i < 20_000 && !stop.get(); i++) {
                    execution.addToolCall(new ToolCallRecord("payment.lookup", Map.of(), "SUCCESS", Map.of(), null, 1));
                    execution.addRagRetrieval(new RagRetrievalRecord("q" + i, "SUCCESS", "a", List.of("src")));
                }
            } catch (Throwable failure) {
                mutatorFailure.set(failure);
            }
        }, "mutator");

        // Mimics AgentAuditClient.recordAgentRun / buildResponse iterating the same lists concurrently.
        Thread reader = new Thread(() -> {
            try {
                while (!stop.get()) {
                    long tools = execution.getToolCalls().stream().count();
                    long rag = execution.getRetrievedContext().stream().count();
                    assertThat(tools).isGreaterThanOrEqualTo(0);
                    assertThat(rag).isGreaterThanOrEqualTo(0);
                }
            } catch (Throwable failure) {
                readerFailure.set(failure);
            }
        }, "reader");

        mutator.start();
        reader.start();
        mutator.join(10_000);
        stop.set(true);
        reader.join(10_000);

        assertThat(mutatorFailure.get()).isNull();
        assertThat(readerFailure.get()).isNull();
    }
}
