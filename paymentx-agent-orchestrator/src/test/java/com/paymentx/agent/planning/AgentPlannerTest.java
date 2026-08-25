package com.paymentx.agent.planning;

import com.paymentx.agent.client.LlmServiceClient;
import com.paymentx.agent.client.McpToolClient;
import com.paymentx.agent.client.PromptServiceClient;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.exception.AgentErrorCodes;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.policy.AgentToolPolicy;
import com.paymentx.agent.registry.AgentCapability;
import com.paymentx.agent.registry.AgentDefinition;
import com.paymentx.agent.registry.AgentRiskLevel;
import com.paymentx.agent.state.AgentExecution;
import com.paymentx.agent.state.AgentPlan;
import com.paymentx.agent.state.PlanAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private static final AgentDefinition DEFINITION = new AgentDefinition("default", "Default", "desc", "1.0",
            Set.of(AgentCapability.PAYMENT_ANALYSIS), Set.of("payment.lookup"),
            "PAYMENTX_AGENT_ORCHESTRATOR", AgentRiskLevel.LOW, true, null, null);

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

        AgentPlan plan = newPlanner().plan(newExecution(), DEFINITION, "corr-1");

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

        AgentPlan plan = newPlanner().plan(newExecution(), DEFINITION, "corr-1");

        assertThat(plan.action()).isEqualTo(PlanAction.FINAL_RESPONSE);
        assertThat(plan.answer()).isEqualTo("the answer");
    }

    @Test
    void plan_malformedJson_throwsPlanParseFailed() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer("not json at all", false));

        assertThatThrownBy(() -> newPlanner().plan(newExecution(), DEFINITION, "corr-1"))
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

        assertThatThrownBy(() -> newPlanner().plan(newExecution(), DEFINITION, "corr-1"))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.PLAN_PARSE_FAILED);
    }

    @Test
    void plan_llmRefused_throwsLlmRefused() {
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer("", true));

        assertThatThrownBy(() -> newPlanner().plan(newExecution(), DEFINITION, "corr-1"))
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

        newPlanner().plan(newExecution(), DEFINITION, "corr-1");

        var variablesCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(promptServiceClient).render(anyString(), variablesCaptor.capture(), any());
        String availableTools = (String) variablesCaptor.getValue().get("availableTools");
        assertThat(availableTools).contains("payment.lookup");
        assertThat(availableTools).doesNotContain("payment.refund");
    }

    @Test
    void plan_availableToolsVariable_isScopedToThisAgentsAllowListEvenWhenMcpKnowsMoreTools() {
        // Phase 4.1 §18 item 12 - "prompt cannot grant additional tools": a tool that is real,
        // MCP-discovered, and even allowed for SOME agent must still be excluded from THIS
        // agent's prompt if it is not in THIS agent's own allowedTools.
        AgentDefinition narrowAgent = new AgentDefinition("narrow", "Narrow", "desc", "1.0",
                Set.of(AgentCapability.DATABASE_ANALYSIS), Set.of("audit.search"),
                "PAYMENTX_AGENT_ORCHESTRATOR", AgentRiskLevel.LOW, true, null, null);
        when(mcpToolClient.listTools()).thenReturn(List.of(
                new McpToolClient.ToolSummary("payment.lookup", "Real read-only lookup - allowed for other agents."),
                new McpToolClient.ToolSummary("audit.search", "Real read-only search.")));
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer(
                "{\"action\":\"FINAL_RESPONSE\",\"reasoning\":\"x\",\"answer\":\"y\"}", false));

        newPlanner().plan(newExecution(), narrowAgent, "corr-1");

        var variablesCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(promptServiceClient).render(anyString(), variablesCaptor.capture(), any());
        String availableTools = (String) variablesCaptor.getValue().get("availableTools");
        assertThat(availableTools).contains("audit.search");
        assertThat(availableTools).doesNotContain("payment.lookup");
    }

    @Test
    void plan_availableToolsVariable_includesRealArgumentSchema_notJustFreeTextDescription() {
        // Phase 4.8.5 remediation regression test - root cause of a live Gemini run producing an
        // INVALID_TOOL_ARGUMENTS payment.lookup call: the LLM previously saw only a free-text
        // description, never the tool's real MCP inputSchema (exact argument key names/types/
        // required-ness), so it had to guess the argument name from prose. This asserts the
        // rendered availableTools text now carries that schema explicitly.
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary(
                "payment.lookup", "Retrieve a payment snapshot.",
                Map.of(
                        "paymentReference", Map.of("type", "string", "description", "The payment reference."),
                        "includeHistory", Map.of("type", "boolean", "description", "Optional timeline flag.")),
                List.of("paymentReference"))));
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer(
                "{\"action\":\"FINAL_RESPONSE\",\"reasoning\":\"x\",\"answer\":\"y\"}", false));

        newPlanner().plan(newExecution(), DEFINITION, "corr-1");

        var variablesCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(promptServiceClient).render(anyString(), variablesCaptor.capture(), any());
        String availableTools = (String) variablesCaptor.getValue().get("availableTools");
        assertThat(availableTools).contains("paymentReference (string, required)");
        assertThat(availableTools).contains("The payment reference.");
        assertThat(availableTools).contains("includeHistory (boolean, optional)");
    }

    @Test
    void plan_llmResponseServedByAFallbackProvider_stillProducesCorrectSchemaConformantToolCall() {
        // Phase 4.8.6 requirement - proves the full chain: (real MCP schema) -> AgentPlanner's
        // rendered prompt -> (whichever provider actually answers) -> correct tool-argument
        // parsing. AgentPlanner is deliberately provider-agnostic by design (it only ever sees
        // LlmServiceClient.LlmAnswer's plain text - see this class's own javadoc: "LLM Service has
        // NO native JSON/tool-calling mode... structured output is achieved entirely through
        // explicit prompt instructions plus defensive parsing here"), so provider.LlmProviderRouter
        // falling back from Gemini to Anthropic (proven separately, deterministically, with zero
        // real Gemini/Anthropic calls, by provider.LlmProviderRouterTest in paymentx-llm-service)
        // is completely invisible at this layer - this test simulates exactly that: a response
        // that arrived via a fallback provider, correctly using the real paymentReference argument
        // name/type this run's schema declared, parsed into a correct CALL_TOOL plan.
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary(
                "payment.lookup", "Retrieve a payment snapshot.",
                Map.of("paymentReference", Map.of("type", "string", "description", "The payment reference.")),
                List.of("paymentReference"))));
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        // Simulates the fallback provider's real answer text - LlmServiceClient has no
        // provider-identity field at all (LlmAnswer is just content+refused), matching how
        // LlmServiceImpl.toResponse already makes provider/fallback identity fully transparent to
        // this service's own REST contract without AgentPlanner ever needing to know it.
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer(
                "{\"action\":\"CALL_TOOL\",\"reasoning\":\"need payment data\",\"tool\":\"payment.lookup\","
                        + "\"arguments\":{\"paymentReference\":\"LIVEE2E-3FE6115058\"}}",
                false));

        AgentPlan plan = newPlanner().plan(newExecution(), DEFINITION, "corr-1");

        assertThat(plan.action()).isEqualTo(PlanAction.CALL_TOOL);
        assertThat(plan.tool()).isEqualTo("payment.lookup");
        assertThat(plan.arguments()).containsEntry("paymentReference", "LIVEE2E-3FE6115058");
    }

    @Test
    void plan_availableToolsVariable_noSchema_rendersDescriptionOnlyWithNoArgumentsLine() {
        // Backward compatibility: the two-arg ToolSummary constructor (every pre-existing call
        // site, including every test above) must keep working exactly as before - no "Arguments:"
        // line when no schema was supplied.
        when(mcpToolClient.listTools()).thenReturn(List.of(new McpToolClient.ToolSummary("payment.lookup", "desc")));
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer(
                "{\"action\":\"FINAL_RESPONSE\",\"reasoning\":\"x\",\"answer\":\"y\"}", false));

        newPlanner().plan(newExecution(), DEFINITION, "corr-1");

        var variablesCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(promptServiceClient).render(anyString(), variablesCaptor.capture(), any());
        String availableTools = (String) variablesCaptor.getValue().get("availableTools");
        assertThat(availableTools).contains("payment.lookup: desc");
        assertThat(availableTools).doesNotContain("Arguments:");
    }

    @Test
    void plan_rendersThisAgentsPromptKey_notThePlatformDefault() {
        AgentDefinition customPromptAgent = new AgentDefinition("custom", "Custom", "desc", "1.0",
                Set.of(), Set.of(), "PAYMENTX_CUSTOM_TEST_PROMPT", AgentRiskLevel.LOW, true, null, null);
        when(mcpToolClient.listTools()).thenReturn(List.of());
        when(promptServiceClient.render(anyString(), anyMap(), any())).thenReturn("rendered prompt");
        when(llmServiceClient.generate(anyString(), any())).thenReturn(new LlmServiceClient.LlmAnswer(
                "{\"action\":\"FINAL_RESPONSE\",\"reasoning\":\"x\",\"answer\":\"y\"}", false));

        newPlanner().plan(newExecution(), customPromptAgent, "corr-1");

        verify(promptServiceClient).render(eq("PAYMENTX_CUSTOM_TEST_PROMPT"), anyMap(), any());
    }
}
