package com.paymentx.agent.planning;

import com.paymentx.agent.client.LlmServiceClient;
import com.paymentx.agent.client.McpToolClient;
import com.paymentx.agent.client.PromptServiceClient;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.exception.AgentErrorCodes;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.policy.AgentToolPolicy;
import com.paymentx.agent.state.AgentExecution;
import com.paymentx.agent.state.AgentPlan;
import com.paymentx.agent.state.PlanAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * English:
 * Mockito-based unit tests for planning/AgentPlanner - PromptServiceClient/
 * LlmServiceClient/McpToolClient are mocked (their own real wire
 * behavior is covered by client/*ClientTest and the real end-to-end
 * controller/AgentE2EIntegrationTest), a REAL AgentToolPolicy is used so
 * the availableTools filtering (Step 25/26) is genuinely exercised.
 * Covers the real defensive JSON parsing this class performs since LLM
 * Service has no native structured-output mode (see AgentPlanner's own
 * javadoc): plain JSON, markdown-code-fenced JSON, malformed JSON, an
 * unrecognized action value, and a real LLM refusal.
 *
 * Hinglish:
 * planning/AgentPlanner ke liye Mockito-based unit tests -
 * PromptServiceClient/LlmServiceClient/McpToolClient mocked hain (unka
 * apna real wire behavior client/*ClientTest aur real end-to-end
 * controller/AgentE2EIntegrationTest se cover hota hai), ek REAL
 * AgentToolPolicy use hoti hai taaki availableTools filtering (Step
 * 25/26) genuinely exercise ho. Ye class ki real defensive JSON parsing
 * cover karta hai kyunki LLM Service ke paas koi native structured-
 * output mode nahi hai (AgentPlanner ka apna javadoc dekho): plain
 * JSON, markdown-code-fenced JSON, malformed JSON, ek unrecognized
 * action value, aur ek real LLM refusal.
 */
@ExtendWith(MockitoExtension.class)
class AgentPlannerTest {

    @Mock
    private PromptServiceClient promptServiceClient;

    @Mock
    private LlmServiceClient llmServiceClient;

    @Mock
    private McpToolClient mcpToolClient;

    private final AgentToolPolicy toolPolicy = new AgentToolPolicy();
    private final AgentOrchestratorProperties properties = new AgentOrchestratorProperties();

    private AgentPlanner newPlanner() {
        return new AgentPlanner(promptServiceClient, llmServiceClient, mcpToolClient, toolPolicy, properties);
    }

    private AgentExecution newExecution() {
        return new AgentExecution("req-1", "corr-1", null, "user-1", "Why did PMT-123 fail?");
    }

    @Test
    void plan_plainJson_parsesRealCallToolPlan() {
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary("payment.lookup", "desc")));
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer(
                "{\"action\":\"CALL_TOOL\",\"reasoning\":\"need status\",\"tool\":\"payment.lookup\",\"arguments\":{\"paymentReference\":\"PMT-123\"}}",
                false));

        AgentPlan plan = newPlanner().plan(newExecution(), "corr-1");

        assertThat(plan.action()).isEqualTo(PlanAction.CALL_TOOL);
        assertThat(plan.tool()).isEqualTo("payment.lookup");
        assertThat(plan.arguments()).containsEntry("paymentReference", "PMT-123");
    }

    @Test
    void plan_markdownFencedJson_isStrippedAndParsed() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer(
                "```json\n{\"action\":\"FINAL_RESPONSE\",\"reasoning\":\"done\",\"answer\":\"the answer\"}\n```", false));

        AgentPlan plan = newPlanner().plan(newExecution(), "corr-1");

        assertThat(plan.action()).isEqualTo(PlanAction.FINAL_RESPONSE);
        assertThat(plan.answer()).isEqualTo("the answer");
    }

    @Test
    void plan_malformedJson_throwsPlanParseFailed() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer("not json at all", false));

        assertThatThrownBy(() -> newPlanner().plan(newExecution(), "corr-1"))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.PLAN_PARSE_FAILED);
    }

    @Test
    void plan_unrecognizedAction_throwsPlanParseFailed() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer(
                "{\"action\":\"DELETE_EVERYTHING\",\"reasoning\":\"x\"}", false));

        assertThatThrownBy(() -> newPlanner().plan(newExecution(), "corr-1"))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.PLAN_PARSE_FAILED);
    }

    @Test
    void plan_llmRefused_throwsLlmRefused() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer("", true));

        assertThatThrownBy(() -> newPlanner().plan(newExecution(), "corr-1"))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.LLM_REFUSED);
    }

    @Test
    void plan_availableToolsVariable_isFilteredThroughRealPolicy() {
        when(mcpToolClient.listTools()).thenReturn(List.of(
                new McpToolClient.ToolSummary("payment.lookup", "Real read-only lookup."),
                new McpToolClient.ToolSummary("payment.refund", "A hypothetical future write tool.")));
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer(
                "{\"action\":\"FINAL_RESPONSE\",\"reasoning\":\"x\",\"answer\":\"y\"}", false));

        newPlanner().plan(newExecution(), "corr-1");

        var variablesCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(promptServiceClient).render(anyString(), variablesCaptor.capture(), any());
        String availableTools = (String) variablesCaptor.getValue().get("availableTools");
        assertThat(availableTools).contains("payment.lookup");
        assertThat(availableTools).doesNotContain("payment.refund");
    }
}
