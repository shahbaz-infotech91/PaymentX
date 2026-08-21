package com.paymentx.agent.planning;

import com.paymentx.agent.client.McpToolClient;
import com.paymentx.agent.exception.AgentErrorCodes;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.policy.AgentToolPolicy;
import com.paymentx.agent.state.AgentPlan;
import com.paymentx.agent.state.PlanAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * Real, non-mocked unit tests for Step 23's strict pre-execution plan
 * validation - uses a REAL policy/AgentToolPolicy so Step 9/30's
 * enforcement is genuinely exercised, not mocked away.
 *
 * Hinglish:
 * Step 23 ke strict pre-execution plan validation ke liye real, non-
 * mocked unit tests - ek REAL policy/AgentToolPolicy use karta hai
 * taaki Step 9/30 ka enforcement genuinely exercise ho, mock karke
 * hataya na jaaye.
 */
class AgentPlanValidatorTest {

    private final AgentPlanValidator validator = new AgentPlanValidator(new AgentToolPolicy());

    private final List<McpToolClient.ToolSummary> discoveredTools = List.of(
            new McpToolClient.ToolSummary("payment.lookup", "desc"),
            new McpToolClient.ToolSummary("audit.search", "desc"));

    @Test
    void validate_validCallToolPlan_doesNotThrow() {
        AgentPlan plan = new AgentPlan(PlanAction.CALL_TOOL, "reason", "payment.lookup", Map.of("paymentReference", "PMT-1"), null, null);
        assertThatCode(() -> validator.validate(plan, discoveredTools)).doesNotThrowAnyException();
    }

    @Test
    void validate_callToolWithMissingToolName_throwsPlanInvalid() {
        AgentPlan plan = new AgentPlan(PlanAction.CALL_TOOL, "reason", null, Map.of(), null, null);
        assertThatThrownBy(() -> validator.validate(plan, discoveredTools))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.PLAN_INVALID);
    }

    @Test
    void validate_callToolWithMissingArguments_throwsPlanInvalid() {
        AgentPlan plan = new AgentPlan(PlanAction.CALL_TOOL, "reason", "payment.lookup", null, null, null);
        assertThatThrownBy(() -> validator.validate(plan, discoveredTools))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.PLAN_INVALID);
    }

    @Test
    void validate_callToolNotInMcpRegistry_throwsPlanInvalid() {
        AgentPlan plan = new AgentPlan(PlanAction.CALL_TOOL, "reason", "routing.lookup", Map.of(), null, null);
        assertThatThrownBy(() -> validator.validate(plan, discoveredTools))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.PLAN_INVALID);
    }

    @Test
    void validate_callToolDeniedByPolicy_throwsToolNotAllowedEvenThoughInRegistry() {
        List<McpToolClient.ToolSummary> registryWithWriteTool = List.of(new McpToolClient.ToolSummary("payment.refund", "desc"));
        AgentPlan plan = new AgentPlan(PlanAction.CALL_TOOL, "reason", "payment.refund", Map.of("paymentReference", "PMT-1"), null, null);

        assertThatThrownBy(() -> validator.validate(plan, registryWithWriteTool))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.TOOL_NOT_ALLOWED);
    }

    @Test
    void validate_validRetrieveKnowledgePlan_doesNotThrow() {
        AgentPlan plan = new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "reason", null, null, "what is a duplicate payment", null);
        assertThatCode(() -> validator.validate(plan, discoveredTools)).doesNotThrowAnyException();
    }

    @Test
    void validate_retrieveKnowledgeMissingQuery_throwsPlanInvalid() {
        AgentPlan plan = new AgentPlan(PlanAction.RETRIEVE_KNOWLEDGE, "reason", null, null, "  ", null);
        assertThatThrownBy(() -> validator.validate(plan, discoveredTools))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.PLAN_INVALID);
    }

    @Test
    void validate_validFinalResponsePlan_doesNotThrow() {
        AgentPlan plan = new AgentPlan(PlanAction.FINAL_RESPONSE, "reason", null, null, null, "the real answer");
        assertThatCode(() -> validator.validate(plan, discoveredTools)).doesNotThrowAnyException();
    }

    @Test
    void validate_finalResponseMissingAnswer_throwsPlanInvalid() {
        AgentPlan plan = new AgentPlan(PlanAction.FINAL_RESPONSE, "reason", null, null, null, null);
        assertThatThrownBy(() -> validator.validate(plan, discoveredTools))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.PLAN_INVALID);
    }

    @Test
    void validate_nullAction_throwsPlanInvalid() {
        AgentPlan plan = new AgentPlan(null, "reason", null, null, null, null);
        assertThatThrownBy(() -> validator.validate(plan, discoveredTools))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.PLAN_INVALID);
    }
}
