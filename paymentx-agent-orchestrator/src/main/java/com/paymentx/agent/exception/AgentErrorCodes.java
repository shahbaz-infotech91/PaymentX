package com.paymentx.agent.exception;

/**
 * English:
 * The normalized error-code list every real Agent Orchestrator
 * infrastructure failure is thrown as - the same "distinguish an
 * infrastructure error from a real, honest terminal outcome" principle
 * RAG Service (Phase 3.6) and MCP Gateway (Phase 3.7) already
 * established. Deliberately does NOT include INSUFFICIENT_CONTEXT/
 * DENIED/MAX_ITERATIONS/TIMEOUT as thrown exceptions - those are real,
 * honest AgentResponseStatus outcomes (HTTP 200, see dto/AgentResponseStatus's
 * javadoc), never exceptions, exactly mirroring RagErrorCodes'
 * INSUFFICIENT_CONTEXT precedent.
 * Why it exists: normalized error handling across client/*,
 * planning/*, and orchestrator/AgentOrchestratorService.
 * How it communicates with other components: every constant here is an
 * AgentException static factory's errorCode; exception/GlobalExceptionHandler
 * renders them into ApiResponse&lt;Void&gt; error bodies.
 *
 * Hinglish:
 * Har real Agent Orchestrator infrastructure failure jis normalized
 * error-code list se throw hoti hai - wahi "ek infrastructure error ko
 * ek real, honest terminal outcome se distinguish karo" principle jo
 * RAG Service (Phase 3.6) aur MCP Gateway (Phase 3.7) already establish
 * kar chuke hain. Jaan-boojh kar INSUFFICIENT_CONTEXT/DENIED/
 * MAX_ITERATIONS/TIMEOUT ko thrown exceptions ke roop me shamil NAHI
 * karta - wo real, honest AgentResponseStatus outcomes hain (HTTP 200,
 * dto/AgentResponseStatus ka javadoc dekho), kabhi exceptions nahi,
 * exactly RagErrorCodes ke INSUFFICIENT_CONTEXT precedent ko mirror
 * karte hue.
 * Ye kyu hai: client/*, planning/*, aur orchestrator/
 * AgentOrchestratorService ke across normalized error handling.
 * Dusre components se kaise communicate karta hai: yahan har constant
 * ek AgentException static factory ka errorCode hai; exception/
 * GlobalExceptionHandler inhe ApiResponse&lt;Void&gt; error bodies me
 * render karta hai.
 */
public final class AgentErrorCodes {

    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String RAG_SERVICE_UNAVAILABLE = "RAG_SERVICE_UNAVAILABLE";
    public static final String MCP_GATEWAY_UNAVAILABLE = "MCP_GATEWAY_UNAVAILABLE";
    public static final String PROMPT_SERVICE_UNAVAILABLE = "PROMPT_SERVICE_UNAVAILABLE";
    public static final String LLM_SERVICE_UNAVAILABLE = "LLM_SERVICE_UNAVAILABLE";
    public static final String LLM_TIMEOUT = "LLM_TIMEOUT";
    public static final String PLAN_PARSE_FAILED = "PLAN_PARSE_FAILED";
    public static final String PLAN_INVALID = "PLAN_INVALID";
    public static final String LLM_REFUSED = "LLM_REFUSED";
    public static final String TOOL_NOT_ALLOWED = "TOOL_NOT_ALLOWED";
    public static final String TOOL_EXECUTION_FAILED = "TOOL_EXECUTION_FAILED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    // Phase 4.1 - agent identity resolution failures, thrown by registry.AgentRegistry.resolve
    // before any planning/tool/RAG work begins for the request.
    public static final String AGENT_NOT_FOUND = "AGENT_NOT_FOUND";
    public static final String AGENT_DISABLED = "AGENT_DISABLED";

    private AgentErrorCodes() {
    }
}
