package com.paymentx.agent.client;

import com.paymentx.agent.config.AgentOrchestratorProperties;
import com.paymentx.agent.exception.AgentException;
import com.paymentx.common.constant.HeaderConstants;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * English:
 * The ONE class in this module that speaks the real Model Context
 * Protocol to MCP Gateway - Step 6/25's explicit requirement that all
 * tool access and tool discovery go through the real, approved MCP
 * protocol, never a REST shortcut to MCP Gateway's internal registry
 * (paymentx-mcp-gateway's own GET /api/v1/mcp/tools is explicitly a
 * redacted, human-observability-only endpoint - see that module's own
 * McpToolCatalogController javadoc for why "real AI clients discover
 * tools through the actual MCP protocol's tools/list method"). Uses the
 * exact same official SDK (io.modelcontextprotocol.sdk:mcp:0.18.3) and
 * client-side transport (HttpClientStreamableHttpTransport) already
 * proven live in paymentx-mcp-gateway's own
 * McpProtocolIntegrationTest - not a new, unverified integration.
 * The single McpSyncClient is built lazily on first real use, not in
 * the constructor - MCP Gateway being temporarily down at THIS
 * service's own startup must never fail Agent Orchestrator's boot (the
 * same "do not couple your own liveness to a downstream dependency's
 * liveness" discipline every other AI Platform service already
 * follows). X-Roles is set ONCE, from AgentOrchestratorProperties'
 * fixed, configured read-only role set (see application.yml's
 * class-level comment for why this is a service-level credential, never
 * per-user), on every outbound HTTP request the transport makes;
 * X-Correlation-Id is re-read per call from a ThreadLocal set by
 * orchestrator/AgentOrchestratorService at the start of each real agent
 * run (Step 37 - propagate the SAME correlation id used by every other
 * downstream client in this service).
 * Why it exists: Step 6/25/26/27/37.
 * How it communicates with other components: injected into
 * orchestrator/AgentOrchestratorService and policy/AgentToolPolicy; the
 * ONLY class in this module that ever calls MCP Gateway.
 *
 * Hinglish:
 * Ye is module ki ek hi class hai jo MCP Gateway se real Model Context
 * Protocol bolti hai - Step 6/25 ka explicit requirement ki har tool
 * access aur tool discovery real, approved MCP protocol se guzare,
 * kabhi MCP Gateway ke internal registry ka ek REST shortcut nahi
 * (paymentx-mcp-gateway ka apna GET /api/v1/mcp/tools explicitly ek
 * redacted, human-observability-only endpoint hai - us module ke apne
 * McpToolCatalogController javadoc dekho ki "real AI clients tools ko
 * actual MCP protocol ke tools/list method se discover karte hain" kyu).
 * Exactly wahi official SDK (io.modelcontextprotocol.sdk:mcp:0.18.3) aur
 * client-side transport (HttpClientStreamableHttpTransport) use karta
 * hai jo paymentx-mcp-gateway ke apne McpProtocolIntegrationTest me
 * already live proven hai - ek naya, unverified integration nahi.
 * Ek hi McpSyncClient pehli real use par lazily banaya jaata hai,
 * constructor me nahi - MCP Gateway ka is service ke apne startup par
 * temporarily down hona Agent Orchestrator ke boot ko kabhi fail nahi
 * karna chahiye (wahi "apni liveness ko ek downstream dependency ki
 * liveness se couple mat karo" discipline jo har doosri AI Platform
 * service already follow karti hai). X-Roles EK BAAR set hota hai,
 * AgentOrchestratorProperties ke fixed, configured read-only role set
 * se (application.yml ka class-level comment dekho ki ye ek service-
 * level credential kyu hai, kabhi per-user nahi), transport jo bhi
 * outbound HTTP request banata hai uske har ek par; X-Correlation-Id
 * har call ke liye ek ThreadLocal se re-read hota hai jise orchestrator/
 * AgentOrchestratorService har real agent run ke start par set karta
 * hai (Step 37 - wahi correlation id propagate karo jo is service ke
 * har doosre downstream client me use hoti hai).
 * Ye kyu hai: Step 6/25/26/27/37.
 * Dusre components se kaise communicate karta hai: orchestrator/
 * AgentOrchestratorService aur policy/AgentToolPolicy me inject hota
 * hai; is module ki ek hi class jo kabhi MCP Gateway ko call karti hai.
 */
@Component
@Slf4j
public class McpToolClient {

    public record ToolSummary(String name, String description) {
    }

    public record ToolCallOutcome(boolean isError, Map<String, Object> structuredContent, String textContent) {
    }

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    private final AgentOrchestratorProperties properties;
    private volatile McpSyncClient client;

    public McpToolClient(AgentOrchestratorProperties properties) {
        this.properties = properties;
    }

    public static void setCorrelationId(String correlationId) {
        CORRELATION_ID.set(correlationId);
    }

    public static void clearCorrelationId() {
        CORRELATION_ID.remove();
    }

    @CircuitBreaker(name = "mcpGateway")
    @Retry(name = "mcpGateway")
    public List<ToolSummary> listTools() {
        try {
            McpSchema.ListToolsResult result = ensureClient().listTools();
            return result.tools().stream().map(tool -> new ToolSummary(tool.name(), tool.description())).toList();
        } catch (Exception mcpFailure) {
            log.warn("MCP Gateway tools/list failed reason={}", mcpFailure.getMessage());
            throw AgentException.mcpGatewayUnavailable("MCP Gateway tool discovery failed: " + mcpFailure.getMessage(), true);
        }
    }

    @CircuitBreaker(name = "mcpGateway")
    @Retry(name = "mcpGateway")
    public ToolCallOutcome callTool(String toolName, Map<String, Object> arguments) {
        try {
            McpSchema.CallToolResult result = ensureClient().callTool(
                    new McpSchema.CallToolRequest(toolName, arguments == null ? Map.of() : arguments));

            Map<String, Object> structured = result.structuredContent() instanceof Map<?, ?> map
                    ? castToStringKeyed(map) : Map.of();
            String text = result.content() != null && !result.content().isEmpty()
                    && result.content().get(0) instanceof McpSchema.TextContent textContent
                    ? textContent.text() : null;

            return new ToolCallOutcome(Boolean.TRUE.equals(result.isError()), structured, text);
        } catch (Exception mcpFailure) {
            log.warn("MCP Gateway tools/call failed toolName={} reason={}", toolName, mcpFailure.getMessage());
            throw AgentException.mcpGatewayUnavailable("MCP Gateway tool call failed: " + mcpFailure.getMessage(), true);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringKeyed(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private McpSyncClient ensureClient() {
        McpSyncClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                String roles = String.join(",", properties.getMcpRoles());
                HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                        .builder(properties.getMcpGatewayUrl() + "/mcp")
                        .connectTimeout(Duration.ofMillis(properties.getMcpConnectTimeoutMs()))
                        .customizeRequest(builder -> {
                            builder.header("X-Roles", roles);
                            String correlationId = CORRELATION_ID.get();
                            if (correlationId != null) {
                                builder.header(HeaderConstants.CORRELATION_ID, correlationId);
                            }
                        })
                        .build();
                McpSyncClient newClient = McpClient.sync(transport)
                        .clientInfo(new McpSchema.Implementation("paymentx-agent-orchestrator", "1.0.0"))
                        .build();
                newClient.initialize();
                client = newClient;
            }
            return client;
        }
    }
}
