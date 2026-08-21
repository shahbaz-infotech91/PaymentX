package com.paymentx.agent.state;

import java.util.Map;

/**
 * English:
 * The strongly-typed, validated shape of one real LLM planning decision
 * (Step 22) - parsed from the LLM's raw JSON text by
 * planning/AgentPlanner, then checked field-by-field by
 * planning/AgentPlanValidator before orchestrator/AgentOrchestratorService
 * ever acts on it (Step 23). `reasoning` is deliberately a short,
 * single-line DECISION LABEL only (e.g. "Payment status required") -
 * never verbose chain-of-thought - this is exactly the
 * `intermediateReasoningMetadata` Step 2 asks for and Step 36 allows to
 * be audited; it is the only "why" the LLM is ever asked to produce,
 * and it is safe to log/return/persist by construction. `tool`/
 * `arguments` are populated only when `action` is CALL_TOOL; `ragQuery`
 * only when RETRIEVE_KNOWLEDGE; `answer` only when FINAL_RESPONSE - the
 * three are mutually exclusive by convention, enforced by
 * AgentPlanValidator, not by this record's shape alone (a Java record
 * cannot express "exactly one of these three is non-null" at the type
 * level).
 * Why it exists: Step 22.
 * How it communicates with other components: built by
 * planning/AgentPlanner.plan(...); validated by
 * planning/AgentPlanValidator.validate(...); consumed by
 * orchestrator/AgentOrchestratorService's loop.
 *
 * Hinglish:
 * Ek real LLM planning decision (Step 22) ka strongly-typed, validated
 * shape - planning/AgentPlanner dwara LLM ke raw JSON text se parse
 * kiya jaata hai, phir planning/AgentPlanValidator dwara field-by-field
 * check hota hai orchestrator/AgentOrchestratorService ke ispar act
 * karne se pehle (Step 23). `reasoning` jaan-boojh kar sirf ek short,
 * single-line DECISION LABEL hai (jaise "Payment status required") -
 * kabhi verbose chain-of-thought nahi - ye exactly wahi
 * `intermediateReasoningMetadata` hai jo Step 2 maangta hai aur Step 36
 * audit karne deta hai; ye ek hi "why" hai jise LLM se kabhi produce
 * karne ko kaha jaata hai, aur ye construction se hi safe hai log/
 * return/persist karne ke liye. `tool`/`arguments` sirf tabhi populate
 * hote hain jab `action` CALL_TOOL ho; `ragQuery` sirf RETRIEVE_KNOWLEDGE
 * ke liye; `answer` sirf FINAL_RESPONSE ke liye - teeno convention se
 * mutually exclusive hain, AgentPlanValidator dwara enforce hota hai,
 * is record ke shape se akele nahi (ek Java record type level par "in
 * teeno me se exactly ek non-null hai" express nahi kar sakta).
 * Ye kyu hai: Step 22.
 * Dusre components se kaise communicate karta hai:
 * planning/AgentPlanner.plan(...) dwara banaya jaata hai;
 * planning/AgentPlanValidator.validate(...) dwara validate hota hai;
 * orchestrator/AgentOrchestratorService ke loop dwara consume hota hai.
 */
public record AgentPlan(
        PlanAction action,
        String reasoning,
        String tool,
        Map<String, Object> arguments,
        String ragQuery,
        String answer
) {
}
