package com.paymentx.mcp.exception;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.common.exception.PaymentXException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * English:
 * Matches every other AI Platform service's GlobalExceptionHandler
 * pattern exactly - converts any exception thrown by this service's one
 * plain @RestController endpoint (controller/McpToolCatalogController)
 * into the same ApiResponse&lt;Void&gt; error shape every PaymentX
 * service uses, never a raw stack trace. Does NOT handle MCP protocol-
 * level tool-call failures - those never reach Spring MVC at all (the
 * MCP servlet is a raw jakarta.servlet.http.HttpServlet, registered
 * outside DispatcherServlet's mapping); ToolInvoker catches McpException
 * itself and renders it as a real MCP CallToolResult(isError=true, ...)
 * (Step 32/33 - see ToolInvoker's javadoc).
 * Why it exists: this platform's established exception-handling
 * convention, applied to the one small REST surface this module has.
 * How it communicates with other components: McpToolCatalogController
 * implicitly relies on this instead of its own try/catch.
 *
 * Hinglish:
 * Har doosri AI Platform service ke GlobalExceptionHandler pattern se
 * exactly match karta hai - is service ke ek hi plain @RestController
 * endpoint (controller/McpToolCatalogController) se throw hui kisi bhi
 * exception ko usi ApiResponse&lt;Void&gt; error shape me convert karta
 * hai jo har PaymentX service use karti hai, kabhi ek raw stack trace
 * nahi. MCP protocol-level tool-call failures ko handle NAHI karta -
 * wo kabhi Spring MVC tak pahunchti hi nahi (MCP servlet ek raw
 * jakarta.servlet.http.HttpServlet hai, DispatcherServlet ki mapping
 * se bahar registered); ToolInvoker khud McpException catch karta hai
 * aur ise ek real MCP CallToolResult(isError=true, ...) ke roop me
 * render karta hai (Step 32/33 - ToolInvoker ka javadoc dekho).
 * Ye kyu hai: is platform ka established exception-handling convention,
 * is module ke ek chhote se REST surface par apply kiya gaya.
 * Dusre components se kaise communicate karta hai: McpToolCatalogController
 * implicitly ispar apne try/catch blocks ke bajaye rely karta hai.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(McpException.class)
    public ResponseEntity<ApiResponse<Void>> handleMcpException(McpException ex, HttpServletRequest request) {
        log.warn("MCP Gateway request failed path={} errorCode={} retryable={} status={}",
                request.getRequestURI(), ex.getErrorCode(), ex.isRetryable(), ex.getHttpStatus());
        return buildResponse(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(PaymentXException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentXException(PaymentXException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(HttpStatus status, String errorCode, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponse.error(ErrorResponse.of(errorCode, message, request.getRequestURI())));
    }
}
