package com.paymentx.agent.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.agent.client.LlmServiceClient;
import com.paymentx.agent.client.McpToolClient;
import com.paymentx.agent.client.PromptServiceClient;
import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.agent.policy.AgentToolPolicy;
import com.paymentx.agent.registry.AgentDefinition;
import com.paymentx.agent.state.AgentExecution;
import com.paymentx.agent.state.AgentPlan;
import com.paymentx.agent.state.PlanAction;
import com.paymentx.agent.state.RagRetrievalRecord;
import com.paymentx.agent.state.ToolCallRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * English:
 * Produces exactly one real, structured planning decision per loop
 * iteration (Step 22) - renders the real, managed
 * PAYMENTX_AGENT_ORCHESTRATOR prompt via client/PromptServiceClient
 * (Step 24, never hardcoded here), calls client/LlmServiceClient with
 * it, and parses the LLM's raw text response into a state/AgentPlan.
 * LLM Service has NO native JSON/tool-calling mode (a real, verified
 * constraint - see paymentx-llm-service's GenerateRequest/GenerateResponse:
 * plain prompt-in/content-out only) - structured output is therefore
 * achieved entirely through explicit prompt instructions (the seeded
 * prompt tells the model to respond with ONLY a JSON object) plus
 * defensive parsing here: strips markdown code fences if the model adds
 * them anyway, extracts the first balanced `{...}` block, and parses it
 * with Jackson. A response that cannot be parsed as valid JSON at all
 * throws AgentException.planParseFailed(...) - this class never
 * "guesses" what a malformed response might have meant (Step 23).
 * Successfully-parsed-but-structurally-wrong plans (missing required
 * fields for the declared action, an unrecognized action value) are
 * still returned as an AgentPlan - planning/AgentPlanValidator, not this
 * class, is responsible for rejecting those (a clean separation between
 * "could this be parsed" and "is this plan allowed to execute").
 * `availableTools` is built from client/McpToolClient.listTools()
 * filtered through policy/AgentToolPolicy.isAllowed(...) (Step 25/26 -
 * "Only expose tools that... are permitted by Agent Policy") - never
 * the raw, unfiltered MCP discovery result.
 * Why it exists: Step 22/24/25/26.
 * How it communicates with other components: called once per iteration
 * by orchestrator/AgentOrchestratorService; calls
 * client/PromptServiceClient, client/LlmServiceClient, and
 * client/McpToolClient.
 *
 * Hinglish:
 * Har loop iteration ke liye exactly ek real, structured planning
 * decision produce karta hai (Step 22) - real, managed
 * PAYMENTX_AGENT_ORCHESTRATOR prompt ko client/PromptServiceClient ke
 * through render karta hai (Step 24, yahan kabhi hardcode nahi), usse
 * client/LlmServiceClient ko call karta hai, aur LLM ke raw text
 * response ko ek state/AgentPlan me parse karta hai. LLM Service ke
 * paas koi native JSON/tool-calling mode NAHI hai (ek real, verified
 * constraint - paymentx-llm-service ka GenerateRequest/GenerateResponse
 * dekho: sirf plain prompt-in/content-out) - structured output isliye
 * poori tarah explicit prompt instructions (seeded prompt model ko
 * kehta hai sirf ek JSON object ke saath respond kare) plus defensive
 * parsing yahan se achieve hota hai: agar model markdown code fences add
 * kare bhi toh unhe strip karta hai, pehla balanced `{...}` block
 * extract karta hai, aur Jackson se parse karta hai. Ek response jise
 * bilkul valid JSON ke roop me parse nahi kiya ja sakta
 * AgentException.planParseFailed(...) throw karta hai - ye class kabhi
 * "guess" nahi karti ki ek malformed response ka matlab kya ho sakta
 * tha (Step 23). Successfully-parsed-lekin-structurally-galat plans
 * (declared action ke liye missing required fields, ek unrecognized
 * action value) phir bhi ek AgentPlan ke roop me return hote hain -
 * planning/AgentPlanValidator, ye class nahi, unhe reject karne ke
 * liye responsible hai (ek clean separation "kya ye parse ho saka" aur
 * "kya ye plan execute karne ki ijazat hai" ke beech). `availableTools`
 * client/McpToolClient.listTools() se banta hai jo policy/AgentToolPolicy.isAllowed(...)
 * se filter hota hai (Step 25/26 - "sirf wahi tools expose karo jo...
 * Agent Policy dwara permitted hain") - kabhi raw, unfiltered MCP
 * discovery result nahi.
 * Ye kyu hai: Step 22/24/25/26.
 * Dusre components se kaise communicate karta hai: orchestrator/
 * AgentOrchestratorService dwara har iteration ek baar call hota hai;
 * client/PromptServiceClient, client/LlmServiceClient, aur
 * client/McpToolClient ko call karta hai.
 */
@Component
@Slf4j
public class AgentPlanner {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final PromptServiceClient promptServiceClient;
    private final LlmServiceClient llmServiceClient;
    private final McpToolClient mcpToolClient;
    private final AgentToolPolicy toolPolicy;
    private final AgentOrchestratorProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentPlanner(PromptServiceClient promptServiceClient, LlmServiceClient llmServiceClient,
                         McpToolClient mcpToolClient, AgentToolPolicy toolPolicy, AgentOrchestratorProperties properties) {
        this.promptServiceClient = promptServiceClient;
        this.llmServiceClient = llmServiceClient;
        this.mcpToolClient = mcpToolClient;
        this.toolPolicy = toolPolicy;
        this.properties = properties;
    }

    // Phase 4.1 - definition is resolved upstream by registry.AgentRegistry and is the ONLY
    // source of promptKey/allowedTools/maxIterations used here; the LLM's own output can never
    // change which definition is in effect (see state.AgentPlan - it has no such field), and
    // this method never reads properties.getAgentPromptKey()/getMaxIterations() directly anymore
    // except as the platform-default fallback when a definition leaves a value unset.
    public AgentPlan plan(AgentExecution execution, AgentDefinition definition, String correlationId) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("availableTools", formatAvailableTools(definition));
        variables.put("executionHistory", formatExecutionHistory(execution));
        variables.put("userQuery", execution.getUserQuery());
        variables.put("iteration", String.valueOf(execution.getIteration()));
        variables.put("maxIterations", String.valueOf(effectiveMaxIterations(definition)));

        String renderedPrompt = promptServiceClient.render(definition.promptKey(), variables, correlationId);
        ToolDefinitionBundle toolBundle = formatToolDefinitions(definition);
        LlmServiceClient.LlmAnswer answer = llmServiceClient.generate(renderedPrompt, correlationId, toolBundle.definitions());

        // Phase 5 - record provider/fallback visibility on the execution (see AgentExecution's
        // own javadoc for the exact semantics: last-call provider, whole-execution-OR fallback
        // flag). Recorded even on a refusal below, since a refusal is still a real answer from a
        // real, identifiable provider.
        execution.setLastLlmProvider(answer.provider());
        if (answer.fallbackUsed()) {
            execution.setFallbackUsedInExecution(true);
            execution.setLastFallbackReason(answer.fallbackReason());
        }

        if (answer.refused()) {
            throw AgentException.llmRefused("LLM declined to produce a planning decision.");
        }

        return resolveRealToolName(parsePlan(answer.content()), toolBundle.sanitizedToRealName());
    }

    // Phase 5.2 fix - OpenAI/Groq's function-calling API rejects a tool name containing "."
    // (regex ^[a-zA-Z0-9_-]+$), but every real MCP tool name is dotted (payment.lookup,
    // database.statistics, ...). Converts only for the outbound, provider-facing tool
    // declaration; the real MCP name is never lost - resolveRealToolName maps the sanitized name
    // the LLM echoes back in its ToolCall straight back to it before anything downstream (policy
    // check, MCP invocation, tool evidence) ever sees the plan.
    private static String sanitizeToolName(String realName) {
        return realName.replace('.', '_');
    }

    private record ToolDefinitionBundle(List<Map<String, Object>> definitions, Map<String, String> sanitizedToRealName) {
    }

    private AgentPlan resolveRealToolName(AgentPlan plan, Map<String, String> sanitizedToRealName) {
        if (plan.tool() == null) {
            return plan;
        }
        String realName = sanitizedToRealName.getOrDefault(plan.tool(), plan.tool());
        if (realName.equals(plan.tool())) {
            return plan;
        }
        return new AgentPlan(plan.action(), plan.reasoning(), realName, plan.arguments(), plan.ragQuery(), plan.answer());
    }

    private int effectiveMaxIterations(AgentDefinition definition) {
        return definition.maxIterations() != null ? definition.maxIterations() : properties.getMaxIterations();
    }

    private String formatAvailableTools(AgentDefinition definition) {
        StringBuilder sb = new StringBuilder();
        for (McpToolClient.ToolSummary tool : mcpToolClient.listTools()) {
            if (toolPolicy.isAllowed(definition, tool.name())) {
                sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append('\n');
                String argumentsLine = formatArguments(tool);
                if (!argumentsLine.isEmpty()) {
                    sb.append("  Arguments: ").append(argumentsLine).append('\n');
                }
            }
        }
        return sb.isEmpty() ? "(no tools currently available)" : sb.toString();
    }

    // Phase 4.8.5 remediation - renders the tool's real MCP inputSchema (exact argument key
    // names, types, required/optional) instead of leaving the LLM to infer an argument name
    // purely from formatAvailableTools' free-text description line above. Root cause of an
    // observed live Gemini run producing an INVALID_TOOL_ARGUMENTS payment.lookup call
    // (2026-08-24) - see McpToolClient.ToolSummary's own javadoc for the full trace. Does not
    // change tool selection, permissions, or any RCA/agent reasoning rule - purely additional,
    // already-correct schema information the MCP protocol response already carried.
    @SuppressWarnings("unchecked")
    private String formatArguments(McpToolClient.ToolSummary tool) {
        Map<String, Object> properties = tool.argumentProperties();
        if (properties == null || properties.isEmpty()) {
            return "";
        }
        java.util.List<String> required = tool.requiredArguments() != null ? tool.requiredArguments() : java.util.List.of();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            first = false;
            String argType = "string";
            String argDescription = null;
            if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                Object type = propertySchema.get("type");
                Object description = propertySchema.get("description");
                if (type != null) {
                    argType = String.valueOf(type);
                }
                if (description != null) {
                    argDescription = String.valueOf(description);
                }
            }
            sb.append(entry.getKey()).append(" (").append(argType).append(", ")
                    .append(required.contains(entry.getKey()) ? "required" : "optional").append(")");
            if (argDescription != null && !argDescription.isBlank()) {
                sb.append(" - ").append(argDescription);
            }
        }
        return sb.toString();
    }

    // Phase 5.2 - formats the actual MCP tool definitions (name, description, inputSchema)
    // as a list of maps for serialization into the LLM Service request body.
    // This is separate from formatAvailableTools which produces free-text for the prompt.
    // Phase 5.2 fix - two corrections: (1) the outbound "name" is now a provider-safe sanitized
    // name (see sanitizeToolName), with the real MCP name preserved in the returned bundle's map;
    // (2) inputSchema is now a complete JSON Schema object ({"type":"object","properties":{...},
    // "required":[...]}), not the bare argumentProperties map - GeminiLlmProvider/GroqLlmProvider/
    // OpenAiLlmProvider all read schema.get("properties")/schema.get("required") from this object,
    // which previously found neither key (argumentProperties itself has no "properties"/"required"
    // keys), so every tool's declared parameters were silently empty regardless of its real schema.
    private ToolDefinitionBundle formatToolDefinitions(AgentDefinition definition) {
        List<Map<String, Object>> definitions = new java.util.ArrayList<>();
        Map<String, String> sanitizedToRealName = new java.util.LinkedHashMap<>();
        for (McpToolClient.ToolSummary tool : mcpToolClient.listTools()) {
            if (toolPolicy.isAllowed(definition, tool.name())) {
                String providerSafeName = sanitizeToolName(tool.name());
                sanitizedToRealName.put(providerSafeName, tool.name());

                Map<String, Object> def = new java.util.LinkedHashMap<>();
                def.put("name", providerSafeName);
                def.put("description", tool.description());

                Map<String, Object> schema = new java.util.LinkedHashMap<>();
                schema.put("type", "object");
                schema.put("properties", tool.argumentProperties() != null ? tool.argumentProperties() : Map.of());
                if (tool.requiredArguments() != null && !tool.requiredArguments().isEmpty()) {
                    schema.put("required", tool.requiredArguments());
                }
                def.put("inputSchema", schema);

                definitions.add(def);
            }
        }
        return new ToolDefinitionBundle(definitions, sanitizedToRealName);
    }

    private String formatExecutionHistory(AgentExecution execution) {
        StringBuilder sb = new StringBuilder();
        for (ToolCallRecord toolCall : execution.getToolCalls()) {
            sb.append("- Called tool ").append(toolCall.toolName())
                    .append(" -> status=").append(toolCall.status())
                    .append(" result=").append(safeToString(toolCall.result())).append('\n');
        }
        for (RagRetrievalRecord ragRetrieval : execution.getRetrievedContext()) {
            sb.append("- Retrieved knowledge for \"").append(ragRetrieval.query())
                    .append("\" -> status=").append(ragRetrieval.status())
                    .append(" answer=").append(ragRetrieval.answer()).append('\n');
        }
        return sb.isEmpty() ? "(nothing gathered yet)" : sb.toString();
    }

    private String safeToString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private AgentPlan parsePlan(String rawContent) {
        String candidate = rawContent == null ? "" : rawContent.trim();
        candidate = candidate.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();

        Matcher matcher = JSON_BLOCK.matcher(candidate);
        if (!matcher.find()) {
            throw AgentException.planParseFailed("LLM planning response did not contain a JSON object.");
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(matcher.group());
        } catch (Exception parseFailure) {
            throw AgentException.planParseFailed("LLM planning response was not valid JSON: " + parseFailure.getMessage());
        }

        String rawAction = node.path("action").asText(null);
        PlanAction action;
        try {
            action = rawAction == null ? null : PlanAction.valueOf(rawAction.trim().toUpperCase());
        } catch (IllegalArgumentException notARealAction) {
            throw AgentException.planParseFailed("LLM planning response had an unrecognized action: " + rawAction);
        }

        Map<String, Object> arguments = null;
        if (node.has("arguments") && node.get("arguments").isObject()) {
            arguments = (Map<String, Object>) objectMapper.convertValue(node.get("arguments"), Map.class);
        }

        return new AgentPlan(
                action,
                node.path("reasoning").asText(null),
                node.path("tool").asText(null),
                arguments,
                node.path("ragQuery").asText(null),
                node.path("answer").asText(null));
    }
}
