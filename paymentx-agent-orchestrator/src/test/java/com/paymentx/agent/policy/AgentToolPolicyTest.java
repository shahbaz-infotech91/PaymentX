package com.paymentx.agent.policy;

import com.paymentx.agent.exception.AgentErrorCodes;
import com.paymentx.agent.exception.AgentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
 * Hinglish:
 * Step 9/30 ke core rule ke liye real, non-mocked unit tests - brief ka
 * sabse important requirement ("PHASE 3.8 KO READ-ONLY AGENT
 * CAPABILITIES SE SHURU HONA CHAHIYE"). Har write-shaped tool naam jo
 * brief naam se leta hai (Step 9 ki exact "DENIED" list) explicitly
 * test karta hai, plus ek unknown/future/invented tool naam, prove
 * karte hue ki default-deny allow-list un sabko deny karti hai, sirf un
 * par nahi jo ek deny-list me anticipate kiye gaye the.
 */
class AgentToolPolicyTest {

    private final AgentToolPolicy policy = new AgentToolPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"payment.lookup", "payment.status", "routing.lookup", "reconciliation.status", "audit.search"})
    void isAllowed_realReadOnlyTools_areAllowed(String toolName) {
        assertThat(policy.isAllowed(toolName)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "payment.retry", "payment.cancel", "payment.refund",
            "participant.update", "routing.update", "settlement.update",
            "database.query", "shell.exec", "not.a.real.tool"
    })
    void isAllowed_writeShapedAndUnknownTools_areNeverAllowed(String toolName) {
        assertThat(policy.isAllowed(toolName)).isFalse();
    }

    @Test
    void checkAllowed_deniedTool_throwsToolNotAllowed() {
        assertThatThrownBy(() -> policy.checkAllowed("payment.refund"))
                .isInstanceOf(AgentException.class)
                .extracting(ex -> ((AgentException) ex).getErrorCode())
                .isEqualTo(AgentErrorCodes.TOOL_NOT_ALLOWED);
    }

    @Test
    void checkAllowed_allowedTool_doesNotThrow() {
        policy.checkAllowed("payment.lookup");
    }

    @Test
    void isAllowed_nullToolName_isNeverAllowed() {
        assertThat(policy.isAllowed(null)).isFalse();
    }
}
