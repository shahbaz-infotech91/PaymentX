package com.paymentx.agent.exception;

import com.paymentx.common.exception.PaymentXException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * English:
 * The single exception type every real Agent Orchestrator
 * infrastructure failure is thrown as - matches every prior AI Platform
 * service's XxxException `errorCode` + `httpStatus` (+ `retryable`)
 * shape exactly (see RagException's/McpException's javadoc for the full
 * rationale this class reuses verbatim). `retryable` drives
 * config/ResilienceConfig's RetryConfigCustomizer predicates.
 * Why it exists: normalized error handling, thrown consistently from
 * client/*, planning/*, and orchestrator/AgentOrchestratorService.
 * How it communicates with other components: caught by
 * orchestrator/AgentOrchestratorService, which converts a real
 * AgentException into an honest terminal AgentResponseStatus (FAILED)
 * rather than ever propagating a raw exception to the caller; also
 * caught by exception/GlobalExceptionHandler for genuinely unexpected
 * failures that occur outside the orchestrator's own try/catch (e.g.
 * request validation).
 *
 * Hinglish:
 * Har real Agent Orchestrator infrastructure failure jis ek exception
 * type se throw hoti hai - har pichli AI Platform service ke XxxException
 * `errorCode` + `httpStatus` (+ `retryable`) shape se exactly match
 * karta hai (poore rationale ke liye RagException/McpException ka
 * javadoc dekho, jise ye class verbatim reuse karti hai). `retryable`
 * config/ResilienceConfig ke RetryConfigCustomizer predicates ko drive
 * karta hai.
 * Ye kyu hai: normalized error handling, client/*, planning/*, aur
 * orchestrator/AgentOrchestratorService se consistently throw ki gayi.
 * Dusre components se kaise communicate karta hai:
 * orchestrator/AgentOrchestratorService ise catch karta hai, jo ek real
 * AgentException ko ek honest terminal AgentResponseStatus (FAILED) me
 * convert karta hai, kabhi caller ko ek raw exception propagate nahi
 * karta; exception/GlobalExceptionHandler bhi ise genuinely unexpected
 * failures ke liye catch karta hai jo orchestrator ke apne try/catch se
 * bahar hoti hain (jaise request validation).
 */
@Getter
public class AgentException extends PaymentXException {

    private final HttpStatus httpStatus;

    public AgentException(String errorCode, String message, boolean retryable, HttpStatus httpStatus) {
        super(errorCode, message, retryable);
        this.httpStatus = httpStatus;
    }

    public static AgentException invalidRequest(String message) {
        return new AgentException(AgentErrorCodes.INVALID_REQUEST, message, false, HttpStatus.BAD_REQUEST);
    }

    public static AgentException ragServiceUnavailable(String message, boolean retryable) {
        return new AgentException(AgentErrorCodes.RAG_SERVICE_UNAVAILABLE, message, retryable, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static AgentException mcpGatewayUnavailable(String message, boolean retryable) {
        return new AgentException(AgentErrorCodes.MCP_GATEWAY_UNAVAILABLE, message, retryable, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static AgentException promptServiceUnavailable(String message, boolean retryable) {
        return new AgentException(AgentErrorCodes.PROMPT_SERVICE_UNAVAILABLE, message, retryable, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static AgentException llmServiceUnavailable(String message, boolean retryable) {
        return new AgentException(AgentErrorCodes.LLM_SERVICE_UNAVAILABLE, message, retryable, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static AgentException llmTimeout(String message) {
        return new AgentException(AgentErrorCodes.LLM_TIMEOUT, message, true, HttpStatus.GATEWAY_TIMEOUT);
    }

    public static AgentException planParseFailed(String message) {
        return new AgentException(AgentErrorCodes.PLAN_PARSE_FAILED, message, false, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static AgentException planInvalid(String message) {
        return new AgentException(AgentErrorCodes.PLAN_INVALID, message, false, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static AgentException llmRefused(String message) {
        return new AgentException(AgentErrorCodes.LLM_REFUSED, message, false, HttpStatus.OK);
    }

    public static AgentException toolNotAllowed(String message) {
        return new AgentException(AgentErrorCodes.TOOL_NOT_ALLOWED, message, false, HttpStatus.FORBIDDEN);
    }

    public static AgentException toolExecutionFailed(String message) {
        return new AgentException(AgentErrorCodes.TOOL_EXECUTION_FAILED, message, false, HttpStatus.BAD_GATEWAY);
    }

    public static AgentException internalError(String message) {
        return new AgentException(AgentErrorCodes.INTERNAL_ERROR, message, false, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
