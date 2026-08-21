package com.paymentx.agent.planning;

import com.paymentx.agent.client.McpToolClient;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.policy.AgentToolPolicy;
import com.paymentx.agent.state.AgentPlan;
import com.paymentx.agent.state.PlanAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * English:
 * Step 23's real, strict, pre-execution validation of a real
 * state/AgentPlan - "validate: action, tool name, arguments,
 * permissions, risk, limits, current state, available tools. If
 * invalid: reject. Do NOT attempt to guess what the LLM meant." Runs
 * AFTER planning/AgentPlanner has successfully parsed JSON but BEFORE
 * orchestrator/AgentOrchestratorService acts on the plan in any way:
 * - action must be a real PlanAction (already guaranteed by the parser,
 *   re-checked here for null-safety).
 * - CALL_TOOL requires a non-blank `tool` AND a non-null `arguments`
 *   map; the tool name must both be in policy/AgentToolPolicy's
 *   allow-list (Step 9/30) AND be a real, currently-discovered MCP tool
 *   (Step 7 - "Tool must exist in MCP Tool Registry AND be enabled...
 *   Never allow the LLM to invent arbitrary tool names") - checked as
 *   two independent conditions, both must hold.
 * - RETRIEVE_KNOWLEDGE requires a non-blank `ragQuery`.
 * - FINAL_RESPONSE requires a non-blank `answer`.
 * Any violation throws AgentException.planInvalid(...) or
 * AgentException.toolNotAllowed(...) - never a silent best-effort
 * correction, never a "the LLM probably meant X" fallback.
 * Why it exists: Step 7/8/9/23/30.
 * How it communicates with other components: called by
 * orchestrator/AgentOrchestratorService immediately after
 * planning/AgentPlanner.plan(...) returns, before any tool/RAG call is
 * ever made from that plan.
 *
 * Hinglish:
 * Step 23 ka real, strict, pre-execution validation ek real
 * state/AgentPlan ka - "validate karo: action, tool name, arguments,
 * permissions, risk, limits, current state, available tools. Agar
 * invalid ho: reject karo. LLM ka matlab kya tha guess karne ki koshish
 * MAT KARO." planning/AgentPlanner ke JSON successfully parse karne ke
 * BAAD chalta hai lekin orchestrator/AgentOrchestratorService ke plan
 * par kisi bhi tarah act karne SE PEHLE:
 * - action ek real PlanAction hona chahiye (parser dwara already
 *   guaranteed, yahan null-safety ke liye phir se check hota hai).
 * - CALL_TOOL ko ek non-blank `tool` AUR ek non-null `arguments` map
 *   chahiye; tool name dono me hona chahiye policy/AgentToolPolicy ki
 *   allow-list me (Step 9/30) AUR ek real, currently-discovered MCP
 *   tool ho (Step 7 - "Tool MCP Tool Registry me exist karna chahiye
 *   AUR enabled hona chahiye... LLM ko arbitrary tool names invent
 *   karne ki ijazat kabhi mat do") - do independent conditions ke roop
 *   me check hota hai, dono hold karni chahiye.
 * - RETRIEVE_KNOWLEDGE ko ek non-blank `ragQuery` chahiye.
 * - FINAL_RESPONSE ko ek non-blank `answer` chahiye.
 * Koi bhi violation AgentException.planInvalid(...) ya
 * AgentException.toolNotAllowed(...) throw karta hai - kabhi ek silent
 * best-effort correction nahi, kabhi "LLM ka probably ye matlab tha"
 * fallback nahi.
 * Ye kyu hai: Step 7/8/9/23/30.
 * Dusre components se kaise communicate karta hai: orchestrator/
 * AgentOrchestratorService dwara planning/AgentPlanner.plan(...) return
 * hone ke turant baad call hota hai, us plan se koi bhi tool/RAG call
 * kabhi hone se pehle.
 */
@Component
@Slf4j
public class AgentPlanValidator {

    private final AgentToolPolicy toolPolicy;

    public AgentPlanValidator(AgentToolPolicy toolPolicy) {
        this.toolPolicy = toolPolicy;
    }

    public void validate(AgentPlan plan, List<McpToolClient.ToolSummary> discoveredTools) {
        if (plan.action() == null) {
            throw AgentException.planInvalid("Plan has no recognizable action.");
        }

        switch (plan.action()) {
            case CALL_TOOL -> validateCallTool(plan, discoveredTools);
            case RETRIEVE_KNOWLEDGE -> {
                if (isBlank(plan.ragQuery())) {
                    throw AgentException.planInvalid("RETRIEVE_KNOWLEDGE plan is missing ragQuery.");
                }
            }
            case FINAL_RESPONSE -> {
                if (isBlank(plan.answer())) {
                    throw AgentException.planInvalid("FINAL_RESPONSE plan is missing answer.");
                }
            }
        }
    }

    private void validateCallTool(AgentPlan plan, List<McpToolClient.ToolSummary> discoveredTools) {
        if (isBlank(plan.tool())) {
            throw AgentException.planInvalid("CALL_TOOL plan is missing tool name.");
        }
        if (plan.arguments() == null) {
            throw AgentException.planInvalid("CALL_TOOL plan is missing arguments.");
        }

        // Step 7 - the tool must be a real, currently-discovered MCP tool, never an invented name.
        boolean existsInRegistry = discoveredTools.stream().anyMatch(tool -> tool.name().equals(plan.tool()));
        if (!existsInRegistry) {
            log.warn("Rejected plan referencing unknown tool={}", plan.tool());
            throw AgentException.planInvalid("Plan referenced a tool that does not exist: " + plan.tool());
        }

        // Step 9/30 - independent, code-enforced policy check; never bypassed by registry presence alone.
        toolPolicy.checkAllowed(plan.tool());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
