package com.paymentx.agent.policy;

import com.paymentx.agent.exception.AgentErrorCodes;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.registry.AgentCapability;
import com.paymentx.agent.registry.AgentDefinition;
import com.paymentx.agent.registry.AgentRiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * English:
 * Real, non-mocked unit tests for Step 9/30's core rule - the brief's
 * single most important requirement ("PHASE 3.8 MUST START WITH READ-
 * ONLY AGENT CAPABILITIES"). Explicitly tests every write-shaped tool
 * name the brief names by name (Step 9's exact "DENIED" list) plus an
 * unknown/future/invented tool name, proving the default-deny allow-
 * list denies all of them, not just the ones anticipated in a deny-
 * list.
 *
 * PHASE 4.1 UPDATE: the allow-list moved from a class-wide constant to
 * registry.AgentDefinition.allowedTools() - these tests now construct a
 * definition carrying the exact same five tools Phase 3.8 hardcoded, so
 * every original scenario/assertion is unchanged, just resolved through
 * the new per-agent parameter. A second definition with a DIFFERENT
 * allow-list proves cross-agent tool isolation - one agent's grant is
 * never another agent's.
 */
class AgentToolPolicyTest {

    private final AgentToolPolicy policy = new AgentToolPolicy();

    private static AgentDefinition defaultLikeDefinition() {
        return new AgentDefinition("default", "Default", "desc", "1.0",
                Set.of(AgentCapability.PAYMENT_ANALYSIS), Set.of(
                "payment.lookup", "payment.status", "routing.lookup", "reconciliation.status", "audit.search"),
                "PAYMENTX_AGENT_ORCHESTRATOR", AgentRiskLevel.LOW, true, null, null);
    }

    private static AgentDefinition narrowDefinition() {
        return new AgentDefinition("narrow-agent", "Narrow", "desc", "1.0",
                Set.of(AgentCapability.DATABASE_ANALYSIS), Set.of("audit.search"),
                "PAYMENTX_AGENT_ORCHESTRATOR", AgentRiskLevel.LOW, true, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment.lookup", "payment.status", "routing.lookup", "reconciliation.status", "audit.search"})
    void isAllowed_realReadOnlyTools_areAllowedForDefaultAgent(String toolName) {
        assertThat(policy.isAllowed(defaultLikeDefinition(), toolName)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "payment.retry", "payment.cancel", "payment.refund",
            "participant.update", "routing.update", "settlement.update",
            "database.query", "shell.exec", "not.a.real.tool"
    })
    void isAllowed_writeShapedAndUnknownTools_areNeverAllowed(String toolName) {
        assertThat(policy.isAllowed(defaultLikeDefinition(), toolName)).isFalse();
    }

    @Test
    void checkAllowed_deniedTool_throwsToolNotAllowed() {
        assertThatThrownBy(() -> policy.checkAllowed(defaultLikeDefinition(), "payment.refund"))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.TOOL_NOT_ALLOWED);
    }

    @Test
    void checkAllowed_allowedTool_doesNotThrow() {
        policy.checkAllowed(defaultLikeDefinition(), "payment.lookup");
    }

    @Test
    void isAllowed_nullToolName_isNeverAllowed() {
        assertThat(policy.isAllowed(defaultLikeDefinition(), null)).isFalse();
    }

    @Test
    void isAllowed_nullDefinition_isNeverAllowed() {
        assertThat(policy.isAllowed(null, "payment.lookup")).isFalse();
    }

    @Test
    void isAllowed_toolGrantedToOneAgent_isNotGrantedToAnotherAgentWithANarrowerAllowList() {
        // Cross-agent isolation (Phase 4.1 §18 item 4/5) - "payment.lookup" is real and allowed
        // for the default agent, but a narrower agent's own allow-list must not inherit it.
        assertThat(policy.isAllowed(defaultLikeDefinition(), "payment.lookup")).isTrue();
        assertThat(policy.isAllowed(narrowDefinition(), "payment.lookup")).isFalse();
        assertThat(policy.isAllowed(narrowDefinition(), "audit.search")).isTrue();
    }
}
