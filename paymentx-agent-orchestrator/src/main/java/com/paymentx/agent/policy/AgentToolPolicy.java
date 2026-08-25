package com.paymentx.agent.policy;

import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.registry.AgentDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PHASE 4.1 UPDATE: the allow-list described below as "fixed, hardcoded" is now per-agent -
 * registry.AgentDefinition.allowedTools(), resolved by registry.AgentRegistry - rather than a
 * single class-wide constant. The default agent's configured allow-list is still exactly the
 * same five tools named below, so single-agent behavior is unchanged; the rest of this javadoc's
 * reasoning (default-deny, checked before MCP Gateway, independent of MCP's own authorization)
 * is otherwise still accurate and kept for history.
 *
 * English:
 * Step 9/30's real, code-enforced tool policy - a fixed, hardcoded,
 * default-deny ALLOW-LIST of the five real read-only tools this agent
 * may ever call (matching MCP Gateway's own real Phase 3.7 catalog
 * exactly - payment.lookup, payment.status, routing.lookup,
 * reconciliation.status, audit.search). ANY other tool name - including
 * every write-shaped tool the brief explicitly names
 * (payment.retry/payment.cancel/payment.refund/participant.update/
 * routing.update/settlement.update) and any tool MCP Gateway might
 * expose in a future phase that this class has never heard of - is
 * denied purely by not appearing on the list (Step 30 - "Even if MCP
 * Gateway contains future write-tool definitions, the Agent MUST NOT be
 * allowed to execute them... Security must be enforced in code"). This
 * is deliberately a default-deny allow-list, not a deny-list of known-
 * bad names - a deny-list only protects against tools it was written to
 * anticipate; an allow-list protects against every tool it was NOT
 * written to anticipate too, which is the stronger, safer property Step
 * 30 actually needs. Checked BEFORE MCP Gateway is ever called (Step
 * 7/23) - a denied tool call never reaches client/McpToolClient at all,
 * let alone MCP Gateway's own real authorization (which still applies
 * independently and redundantly on every call that does pass this
 * check - defense in depth, not a substitute for it).
 * Why it exists: Step 9/30 - the brief's single most important rule
 * ("PHASE 3.8 MUST START WITH READ-ONLY AGENT CAPABILITIES... The
 * following MUST remain disabled").
 * How it communicates with other components: called by
 * planning/AgentPlanValidator for every CALL_TOOL plan, before
 * orchestrator/AgentOrchestratorService ever invokes
 * client/McpToolClient.
 *
 * Hinglish:
 * Step 9/30 ka real, code-enforced tool policy - un paanch real read-
 * only tools ki ek fixed, hardcoded, default-deny ALLOW-LIST jinhe ye
 * agent kabhi call kar sakta hai (MCP Gateway ke apne real Phase 3.7
 * catalog se exactly match karte hue - payment.lookup, payment.status,
 * routing.lookup, reconciliation.status, audit.search). Koi bhi doosra
 * tool naam - including har write-shaped tool jise brief explicitly
 * naam leta hai (payment.retry/payment.cancel/payment.refund/
 * participant.update/routing.update/settlement.update) aur koi bhi tool
 * jise MCP Gateway kisi future phase me expose kare jiske baare me is
 * class ne kabhi suna hi nahi - sirf list par na hone ki wajah se deny
 * ho jaata hai (Step 30 - "Chahe MCP Gateway future write-tool
 * definitions rakhta ho, Agent ko unhe execute karne ki ijazat NAHI
 * honi chahiye... Security code me enforce honi chahiye"). Ye jaan-
 * boojh kar ek default-deny allow-list hai, known-bad names ki ek deny-
 * list nahi - ek deny-list sirf un tools se protect karti hai jinke
 * liye ye likhi gayi thi; ek allow-list har us tool se bhi protect
 * karti hai jiske liye ye NAHI likhi gayi thi, jo Step 30 ko actually
 * chahiye wali stronger, safer property hai. MCP Gateway ko kabhi call
 * karne SE PEHLE check hota hai (Step 7/23) - ek denied tool call kabhi
 * client/McpToolClient tak pahunchti hi nahi, MCP Gateway ki apni real
 * authorization tak toh bilkul nahi (jo har us call par independently
 * aur redundantly apply hoti hai jo ye check pass karti hai - defense
 * in depth, iska substitute nahi).
 * Ye kyu hai: Step 9/30 - brief ka sabse important rule ("PHASE 3.8 KO
 * READ-ONLY AGENT CAPABILITIES SE SHURU HONA CHAHIYE... Ye disabled
 * rehna chahiye").
 * Dusre components se kaise communicate karta hai: planning/
 * AgentPlanValidator dwara har CALL_TOOL plan ke liye call hota hai,
 * orchestrator/AgentOrchestratorService ke client/McpToolClient invoke
 * karne se pehle.
 */
@Component
@Slf4j
public class AgentToolPolicy {

    // Phase 4.1: the fixed, hardcoded, single-agent default-deny allow-list this class used to
    // own directly has moved to registry.AgentDefinition.allowedTools() - one per agent, resolved
    // by registry.AgentRegistry and passed in by the caller. This class is now a stateless
    // checker of "is toolName in THIS agent's allow-list", but the property that made it Phase
    // 3.8's single most important security rule is unchanged: still a default-deny allow-list
    // (an unknown/invented/future tool name is denied purely by absence, never by pattern-
    // matching known-bad names), still checked BEFORE client.McpToolClient is ever touched, still
    // independent of - never a substitute for - MCP Gateway's own ToolAuthorizationService.
    public boolean isAllowed(AgentDefinition definition, String toolName) {
        return toolName != null && definition != null && definition.allowedTools().contains(toolName);
    }

    public void checkAllowed(AgentDefinition definition, String toolName) {
        if (!isAllowed(definition, toolName)) {
            log.warn("Agent tool policy denied agentId={} toolName={}",
                    definition != null ? definition.agentId() : "unknown", toolName);
            throw AgentException.toolNotAllowed("Tool is not permitted for agent '"
                    + (definition != null ? definition.agentId() : "unknown") + "': " + toolName);
        }
    }
}
