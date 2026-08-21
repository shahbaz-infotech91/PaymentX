package com.paymentx.agent.state;

/**
 * English:
 * Step 22's exact three-value structured action model - the ONLY three
 * things one planning decision may ever request. `CALL_TOOL` requires
 * `tool`+`arguments`; `RETRIEVE_KNOWLEDGE` requires `ragQuery`;
 * `FINAL_RESPONSE` requires `answer`. There is no fourth, free-form
 * action - "Do NOT allow free-form text to directly trigger tool calls.
 * Use a strongly validated structured action model" (Step 22).
 * Why it exists: Step 22/23.
 * How it communicates with other components: state/AgentPlan.action();
 * validated by planning/AgentPlanValidator before
 * orchestrator/AgentOrchestratorService ever acts on it.
 *
 * Hinglish:
 * Step 22 ka exact teen-value structured action model - sirf yehi teen
 * cheezein hain jo ek planning decision kabhi request kar sakta hai.
 * `CALL_TOOL` ko `tool`+`arguments` chahiye; `RETRIEVE_KNOWLEDGE` ko
 * `ragQuery` chahiye; `FINAL_RESPONSE` ko `answer` chahiye. Koi chautha,
 * free-form action nahi hai - "Free-form text ko seedhe tool calls
 * trigger karne ki ijazat MAT DO. Ek strongly validated structured
 * action model use karo" (Step 22).
 * Ye kyu hai: Step 22/23.
 * Dusre components se kaise communicate karta hai: state/AgentPlan.action();
 * orchestrator/AgentOrchestratorService dwara ispar act karne se pehle
 * planning/AgentPlanValidator dwara validate hota hai.
 */
public enum PlanAction {
    CALL_TOOL,
    RETRIEVE_KNOWLEDGE,
    FINAL_RESPONSE
}
