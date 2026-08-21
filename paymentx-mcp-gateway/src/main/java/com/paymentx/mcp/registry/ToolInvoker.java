package com.paymentx.mcp.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.mcp.audit.McpAuditClient;
import com.paymentx.mcp.exception.McpErrorCodes;
import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.metrics.McpMetrics;
import com.paymentx.mcp.ratelimit.ToolCallRateLimiter;
import com.paymentx.mcp.security.ToolAuthorizationService;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * English:
 * THE single real dispatch point for every MCP tool call - config/
 * McpServerConfig's callHandler for every registered tool ultimately
 * calls invoke() here, so this class is where Steps 12/15/20/23/26/29/
 * 32/33 all actually happen, in order, for every call:
 * 1. registry/ToolRegistry.findByName - TOOL_NOT_FOUND if unknown
 *    (never a silent no-op).
 * 2. definition().enabled() check - TOOL_DISABLED.
 * 3. security/ToolAuthorizationService.checkPermission - TOOL_UNAUTHORIZED/
 *    TOOL_FORBIDDEN/WRITE_OPERATION_NOT_ALLOWED, BEFORE the tool ever
 *    sees the caller's arguments (Step 12 - "AI is NOT trusted").
 * 4. ratelimit/ToolCallRateLimiter.checkAndConsume - TOOL_RATE_LIMITED.
 * 5. tool.execute(context, arguments), run on a bounded executor and
 *    forcibly bounded by definition().timeout() - TOOL_TIMEOUT if it
 *    does not return in time (Step 23 - "never allow an AI tool
 *    invocation to hang indefinitely"). The tool itself does its own
 *    input validation (INVALID_TOOL_ARGUMENTS) and resource-level
 *    authorization (RESOURCE_FORBIDDEN) as part of this step.
 * 6. audit/McpAuditClient.recordToolInvocation - ALWAYS, success or
 *    failure, in a finally block (Step 29 - "every tool invocation must
 *    be auditable"), but never allowed to affect the response even if
 *    the audit write itself fails (see McpAuditClient's javadoc).
 * 7. metrics/McpMetrics - every real outcome (success, denied, timeout,
 *    rate-limited, failed) is counted (Step 30).
 * Every McpException caught here is converted into a real MCP
 * CallToolResult with isError=true and a structured content block
 * carrying {errorCode, message} - NEVER a raw Java exception/stack
 * trace, and NEVER an MCP protocol-level JSON-RPC error for a tool-
 * level failure (Step 32/33 - per the MCP specification itself, tool
 * execution errors belong in the result object so the calling AI can
 * see and reason about them, not as a transport-level failure it can't
 * inspect). A tool's own successful found=false ("no such payment")
 * result passes through completely unchanged - isError stays false.
 * Why it exists: centralizes the entire cross-cutting security/
 * reliability contract in one place instead of duplicating it across
 * five tool classes.
 * How it communicates with other components: called by config/
 * McpServerConfig's per-tool callHandler; calls registry/ToolRegistry,
 * security/ToolAuthorizationService, ratelimit/ToolCallRateLimiter,
 * audit/McpAuditClient, metrics/McpMetrics, and the resolved
 * PaymentXTool itself.
 *
 * Hinglish:
 * Ye har MCP tool call ka EK real dispatch point hai - config/
 * McpServerConfig ke har registered tool ka callHandler ultimately
 * yahan invoke() call karta hai, isliye ye class wahi jagah hai jahan
 * Steps 12/15/20/23/26/29/32/33 sab actually hote hain, order me, har
 * call ke liye:
 * 1. registry/ToolRegistry.findByName - TOOL_NOT_FOUND agar unknown ho
 *    (kabhi ek silent no-op nahi).
 * 2. definition().enabled() check - TOOL_DISABLED.
 * 3. security/ToolAuthorizationService.checkPermission -
 *    TOOL_UNAUTHORIZED/TOOL_FORBIDDEN/WRITE_OPERATION_NOT_ALLOWED, tool
 *    ke caller ke arguments dekhne SE PEHLE (Step 12 - "AI TRUSTED NAHI
 *    hai").
 * 4. ratelimit/ToolCallRateLimiter.checkAndConsume - TOOL_RATE_LIMITED.
 * 5. tool.execute(context, arguments), ek bounded executor par chalta
 *    hai aur definition().timeout() se forcibly bounded hota hai -
 *    TOOL_TIMEOUT agar time par return nahi hota (Step 23 - "ek AI tool
 *    invocation ko kabhi indefinitely hang hone ki ijazat mat do"). Tool
 *    khud apna input validation (INVALID_TOOL_ARGUMENTS) aur resource-
 *    level authorization (RESOURCE_FORBIDDEN) is step ke hisse ke roop
 *    me karta hai.
 * 6. audit/McpAuditClient.recordToolInvocation - HAMESHA, success ya
 *    failure, ek finally block me (Step 29 - "har tool invocation
 *    auditable hona chahiye"), lekin response ko kabhi affect nahi karta
 *    chahe audit write khud fail ho jaaye (McpAuditClient ka javadoc
 *    dekho).
 * 7. metrics/McpMetrics - har real outcome (success, denied, timeout,
 *    rate-limited, failed) count hota hai (Step 30).
 * Yahan catch hui har McpException ek real MCP CallToolResult me
 * convert hoti hai isError=true ke saath aur ek structured content block
 * {errorCode, message} carry karti hai - KABHI ek raw Java exception/
 * stack trace nahi, aur KABHI ek MCP protocol-level JSON-RPC error nahi
 * ek tool-level failure ke liye (Step 32/33 - khud MCP specification ke
 * hisaab se, tool execution errors result object me hone chahiye taaki
 * calling AI unhe dekh sake aur unke baare me reason kar sake, ek
 * transport-level failure ke roop me nahi jise wo inspect nahi kar
 * sakta). Ek tool ka apna successful found=false ("aisa koi payment
 * nahi hai") result bilkul unchanged pass hota hai - isError false hi
 * rehta hai.
 * Ye kyu hai: poore cross-cutting security/reliability contract ko ek
 * jagah centralize karta hai, ise paanch tool classes me duplicate karne
 * ke bajaye.
 * Dusre components se kaise communicate karta hai: config/McpServerConfig
 * ke per-tool callHandler dwara call hota hai; registry/ToolRegistry,
 * security/ToolAuthorizationService, ratelimit/ToolCallRateLimiter,
 * audit/McpAuditClient, metrics/McpMetrics, aur resolved PaymentXTool
 * khud ko call karta hai.
 */
@Component
@Slf4j
public class ToolInvoker {

    private final ToolRegistry toolRegistry;
    private final ToolAuthorizationService authorizationService;
    private final ToolCallRateLimiter rateLimiter;
    private final McpAuditClient auditClient;
    private final McpMetrics metrics;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService toolExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "mcp-tool-exec");
        thread.setDaemon(true);
        return thread;
    });

    public ToolInvoker(ToolRegistry toolRegistry, ToolAuthorizationService authorizationService,
                        ToolCallRateLimiter rateLimiter, McpAuditClient auditClient, McpMetrics metrics) {
        this.toolRegistry = toolRegistry;
        this.authorizationService = authorizationService;
        this.rateLimiter = rateLimiter;
        this.auditClient = auditClient;
        this.metrics = metrics;
    }

    public McpSchema.CallToolResult invoke(String toolName, Map<String, Object> arguments, ToolInvocationContext context) {
        metrics.recordRequest();
        long startTime = System.currentTimeMillis();
        Timer.Sample timerSample = metrics.startTimer();
        String errorCategory = null;
        String executionStatus = "SUCCESS";
        String riskLevel = "UNKNOWN";
        String targetService = "unknown";

        try {
            PaymentXTool tool = toolRegistry.findByName(toolName).orElseThrow(() -> McpException.toolNotFound(toolName));
            riskLevel = tool.definition().riskLevel().name();
            targetService = tool.definition().auditClassification();

            if (!tool.definition().enabled()) {
                throw McpException.toolDisabled(toolName);
            }

            metrics.recordToolCall(toolName);
            authorizationService.checkPermission(context, tool.definition());
            rateLimiter.checkAndConsume(context.callerId(), toolName);

            Map<String, Object> result = executeWithTimeout(tool, context, arguments == null ? Map.of() : arguments);

            metrics.recordSuccess(toolName);
            return successResult(result);
        } catch (McpException ex) {
            executionStatus = "FAILED";
            errorCategory = ex.getErrorCode();
            recordFailureMetrics(toolName, ex);
            return errorResult(ex);
        } catch (Exception unexpected) {
            executionStatus = "FAILED";
            errorCategory = McpErrorCodes.TOOL_EXECUTION_FAILED;
            log.error("Unexpected MCP tool failure toolName={}", toolName, unexpected);
            metrics.recordFailure(toolName, McpErrorCodes.TOOL_EXECUTION_FAILED);
            return errorResult(McpException.executionFailed("Unexpected failure executing tool: " + toolName));
        } finally {
            metrics.stopTimer(timerSample, toolName);
            long durationMs = System.currentTimeMillis() - startTime;
            auditClient.recordToolInvocation(context, toolName, riskLevel, "CHECKED", executionStatus, durationMs,
                    targetService, errorCategory, null, context.participantId());
        }
    }

    private Map<String, Object> executeWithTimeout(PaymentXTool tool, ToolInvocationContext context, Map<String, Object> arguments) {
        CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(
                () -> tool.execute(context, arguments), toolExecutor);
        try {
            return future.get(tool.definition().timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timedOut) {
            future.cancel(true);
            throw McpException.timeout("Tool call timed out after " + tool.definition().timeout() + ": " + tool.definition().name());
        } catch (ExecutionException wrapped) {
            if (wrapped.getCause() instanceof McpException mcpException) {
                throw mcpException;
            }
            // Never surface an unanticipated exception's own message here (Step 32/33's "NEVER a raw Java
            // exception/stack trace" contract) - it could carry arbitrary internal detail. Log the real
            // cause server-side; the caller only ever sees a generic, tool-name-scoped message.
            log.error("Unexpected tool execution failure toolName={}", tool.definition().name(), wrapped.getCause());
            throw McpException.executionFailed("Unexpected failure executing tool: " + tool.definition().name());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw McpException.executionFailed("Tool execution was interrupted: " + tool.definition().name());
        }
    }

    private void recordFailureMetrics(String toolName, McpException ex) {
        switch (ex.getErrorCode()) {
            case McpErrorCodes.TOOL_TIMEOUT -> metrics.recordTimeout(toolName);
            case McpErrorCodes.TOOL_RATE_LIMITED -> metrics.recordRateLimited(toolName);
            case McpErrorCodes.TOOL_UNAUTHORIZED, McpErrorCodes.TOOL_FORBIDDEN,
                 McpErrorCodes.RESOURCE_FORBIDDEN, McpErrorCodes.WRITE_OPERATION_NOT_ALLOWED,
                 McpErrorCodes.APPROVAL_REQUIRED -> {
                metrics.recordAuthorizationFailure(toolName);
                metrics.recordDenied(toolName, ex.getErrorCode());
            }
            case McpErrorCodes.INVALID_TOOL_ARGUMENTS -> metrics.recordValidationFailure(toolName);
            default -> metrics.recordFailure(toolName, ex.getErrorCode());
        }
    }

    private McpSchema.CallToolResult successResult(Map<String, Object> result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .structuredContent(result)
                    .isError(false)
                    .build();
        } catch (Exception serializationFailure) {
            throw McpException.executionFailed("Tool result could not be serialized: " + serializationFailure.getMessage());
        }
    }

    private McpSchema.CallToolResult errorResult(McpException ex) {
        Map<String, Object> errorBody = Map.of("errorCode", ex.getErrorCode(), "message", ex.getMessage());
        String json;
        try {
            json = objectMapper.writeValueAsString(errorBody);
        } catch (Exception ignored) {
            json = "{\"errorCode\":\"" + ex.getErrorCode() + "\"}";
        }
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(json)))
                .structuredContent(errorBody)
                .isError(true)
                .build();
    }

    @PreDestroy
    public void shutdown() {
        toolExecutor.shutdown();
    }
}
