package com.paymentx.mcp.exception;

import com.paymentx.common.exception.PaymentXException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * English:
 * The single exception type every real MCP Gateway failure is thrown
 * as - matches LLM/Embedding/Vector/RAG Service's XxxException
 * `errorCode` + `httpStatus` (+ `retryable`) shape exactly (see
 * RagException's javadoc for the full rationale this class reuses
 * verbatim). `retryable` drives config/ResilienceConfig's four
 * RetryConfigCustomizer predicates - a downstream 503/timeout is
 * retryable=true, a downstream 4xx (bad request, forbidden) or an
 * authorization/validation failure is retryable=false, matching Step
 * 24's "read-only tool: limited retry may be acceptable... do NOT
 * retry invalid request/authorization failure."
 * Why it exists: Step 32's exact required error-code list, thrown
 * consistently from ToolInvoker, security/ToolAuthorizationService,
 * ratelimit/ToolCallRateLimiter, and every client/ class.
 * How it communicates with other components: thrown throughout this
 * module; ToolInvoker catches it and maps errorCode -&gt; a real MCP
 * CallToolResult(isError=true, ...) so the AI sees a structured,
 * honest failure reason instead of a generic tool crash (Step 32/33);
 * exception/GlobalExceptionHandler catches it for the one plain REST
 * endpoint this service exposes.
 *
 * Hinglish:
 * Har real MCP Gateway failure jis ek exception type se throw hoti hai
 * - LLM/Embedding/Vector/RAG Service ke XxxException `errorCode` +
 * `httpStatus` (+ `retryable`) shape se exactly match karta hai
 * (poore rationale ke liye RagException ka javadoc dekho, jise ye
 * class verbatim reuse karti hai). `retryable`
 * config/ResilienceConfig ke char RetryConfigCustomizer predicates ko
 * drive karta hai - ek downstream 503/timeout retryable=true hai, ek
 * downstream 4xx (bad request, forbidden) ya ek authorization/
 * validation failure retryable=false hai, Step 24 se match karte hue -
 * "read-only tool: limited retry acceptable ho sakta hai... invalid
 * request/authorization failure par retry MAT karo."
 * Ye kyu hai: Step 32 ki exact required error-code list, ToolInvoker,
 * security/ToolAuthorizationService, ratelimit/ToolCallRateLimiter, aur
 * har client/ class se consistently throw ki gayi.
 * Dusre components se kaise communicate karta hai: is module me poori
 * jagah throw hoti hai; ToolInvoker ise catch karta hai aur errorCode
 * ko ek real MCP CallToolResult(isError=true, ...) me map karta hai
 * taaki AI ko ek structured, honest failure reason dikhe, ek generic
 * tool crash nahi (Step 32/33); exception/GlobalExceptionHandler ise is
 * service ke ek hi plain REST endpoint ke liye catch karta hai.
 */
@Getter
public class McpException extends PaymentXException {

    private final HttpStatus httpStatus;

    public McpException(String errorCode, String message, boolean retryable, HttpStatus httpStatus) {
        super(errorCode, message, retryable);
        this.httpStatus = httpStatus;
    }

    public static McpException toolNotFound(String toolName) {
        return new McpException(McpErrorCodes.TOOL_NOT_FOUND, "No such tool: " + toolName, false, HttpStatus.NOT_FOUND);
    }

    public static McpException toolDisabled(String toolName) {
        return new McpException(McpErrorCodes.TOOL_DISABLED, "Tool is disabled: " + toolName, false, HttpStatus.FORBIDDEN);
    }

    public static McpException invalidArguments(String message) {
        return new McpException(McpErrorCodes.INVALID_TOOL_ARGUMENTS, message, false, HttpStatus.BAD_REQUEST);
    }

    public static McpException unauthorized(String message) {
        return new McpException(McpErrorCodes.TOOL_UNAUTHORIZED, message, false, HttpStatus.UNAUTHORIZED);
    }

    public static McpException forbidden(String message) {
        return new McpException(McpErrorCodes.TOOL_FORBIDDEN, message, false, HttpStatus.FORBIDDEN);
    }

    public static McpException resourceForbidden(String message) {
        return new McpException(McpErrorCodes.RESOURCE_FORBIDDEN, message, false, HttpStatus.FORBIDDEN);
    }

    public static McpException timeout(String message) {
        return new McpException(McpErrorCodes.TOOL_TIMEOUT, message, true, HttpStatus.GATEWAY_TIMEOUT);
    }

    public static McpException rateLimited(String message) {
        return new McpException(McpErrorCodes.TOOL_RATE_LIMITED, message, true, HttpStatus.TOO_MANY_REQUESTS);
    }

    public static McpException targetServiceUnavailable(String message, boolean retryable) {
        return new McpException(McpErrorCodes.TARGET_SERVICE_UNAVAILABLE, message, retryable, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static McpException executionFailed(String message) {
        return new McpException(McpErrorCodes.TOOL_EXECUTION_FAILED, message, false, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static McpException writeOperationNotAllowed(String toolName) {
        return new McpException(McpErrorCodes.WRITE_OPERATION_NOT_ALLOWED,
                "Write tool is not enabled in this phase: " + toolName, false, HttpStatus.FORBIDDEN);
    }

    public static McpException approvalRequired(String toolName) {
        return new McpException(McpErrorCodes.APPROVAL_REQUIRED,
                "Tool requires human approval, which is not implemented in this phase: " + toolName, false, HttpStatus.FORBIDDEN);
    }
}
