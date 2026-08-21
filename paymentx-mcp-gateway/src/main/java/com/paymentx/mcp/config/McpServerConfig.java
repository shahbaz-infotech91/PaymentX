package com.paymentx.mcp.config;

import com.paymentx.common.constant.HeaderConstants;
import com.paymentx.mcp.registry.McpToolDefinition;
import com.paymentx.mcp.registry.PaymentXTool;
import com.paymentx.mcp.registry.ToolInvocationContext;
import com.paymentx.mcp.registry.ToolInvoker;
import com.paymentx.mcp.registry.ToolRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * English:
 * Wires the real Model Context Protocol server (Step 3/4) - the actual
 * AI-facing boundary this whole module exists for. Uses the official
 * io.modelcontextprotocol.sdk (version 0.18.3, verified live via `mvn
 * dependency:get` + `javap` against the real jar - see pom.xml's
 * comment), NOT a hand-rolled protocol pretending to be MCP (Step 3's
 * explicit "do NOT invent a proprietary protocol and call it MCP").
 * Transport: HttpServletStreamableServerTransportProvider - the SDK's
 * real, current-spec (protocolVersions() reports up to 2025-11-25)
 * Streamable HTTP transport (Step 4's preferred choice over legacy
 * SSE-only or a dev-only stdio server), implemented as a plain
 * jakarta.servlet.http.HttpServlet - registered directly into this
 * service's own embedded Tomcat via ServletRegistrationBean at /mcp, no
 * Spring AI, no WebFlux/Netty, matching this platform's "raw official
 * SDK" convention.
 * contextExtractor is the load-bearing piece for Step 12 ("AI is NOT
 * trusted"): it runs synchronously inside the servlet's own doPost(),
 * reading the real, already-trusted X-Roles/X-Participant-Id headers
 * directly off the raw HttpServletRequest (the same headers
 * HeaderRoleAuthenticationFilter reads elsewhere in this platform) into
 * an immutable McpTransportContext BEFORE any of the SDK's internal
 * async/reactive dispatch happens - later, inside a tool's callHandler
 * (which may run on a different thread than the original servlet
 * request), exchange.transportContext() reliably returns the same
 * values. This is deliberately NOT done via Spring Security's
 * SecurityContextHolder (a ThreadLocal, not reliably propagated across
 * the SDK's internal reactor scheduling) - see SecurityConfig's javadoc
 * for the full reasoning.
 * Every registered tool's real callHandler does exactly one thing:
 * build a ToolInvocationContext from the exchange's transportContext()
 * and delegate to ToolInvoker.invoke() - all of Steps 12-33's actual
 * enforcement lives in ToolInvoker/security/ratelimit/audit, not here.
 * Why it exists: Step 3/4/5/6.
 * How it communicates with other components: converts every
 * registry/McpToolDefinition from registry/ToolRegistry.all() into a
 * real McpSchema.Tool + McpServerFeatures.SyncToolSpecification at
 * startup; every tool call is delegated to registry/ToolInvoker.
 *
 * Hinglish:
 * Real Model Context Protocol server wire karta hai (Step 3/4) - is
 * poore module ka AI-facing boundary jiske liye ye exist karta hai.
 * Official io.modelcontextprotocol.sdk use karta hai (version 0.18.3,
 * `mvn dependency:get` + `javap` se real jar ke against live verify
 * kiya gaya - pom.xml ka comment dekho), ek hand-rolled protocol nahi
 * jo MCP hone ka dawa kare (Step 3 ka explicit "ek proprietary protocol
 * invent MAT karo aur use MCP mat kaho"). Transport:
 * HttpServletStreamableServerTransportProvider - SDK ka real, current-
 * spec (protocolVersions() 2025-11-25 tak report karta hai) Streamable
 * HTTP transport (Step 4 ka preferred choice legacy SSE-only ya ek dev-
 * only stdio server ke upar), ek plain jakarta.servlet.http.HttpServlet
 * ke roop me implement kiya gaya - is service ke apne embedded Tomcat
 * me seedhe ServletRegistrationBean ke through /mcp par register hota
 * hai, koi Spring AI nahi, koi WebFlux/Netty nahi, is platform ke "raw
 * official SDK" convention se match karte hue.
 * contextExtractor Step 12 ("AI TRUSTED NAHI hai") ke liye load-bearing
 * piece hai: ye synchronously servlet ke apne doPost() ke andar chalta
 * hai, real, already-trusted X-Roles/X-Participant-Id headers ko seedhe
 * raw HttpServletRequest se padhta hai (wahi headers jo
 * HeaderRoleAuthenticationFilter is platform me kahin aur padhta hai)
 * ek immutable McpTransportContext me, SDK ke internal async/reactive
 * dispatch hone SE PEHLE - baad me, ek tool ke callHandler ke andar (jo
 * original servlet request se ek alag thread par chal sakta hai),
 * exchange.transportContext() reliably wahi values return karta hai. Ye
 * jaan-boojh kar Spring Security ke SecurityContextHolder (ek
 * ThreadLocal, SDK ke internal reactor scheduling ke across reliably
 * propagate nahi hota) ke through nahi kiya gaya - poore reasoning ke
 * liye SecurityConfig ka javadoc dekho.
 * Har registered tool ka real callHandler exactly ek cheez karta hai:
 * exchange ke transportContext() se ek ToolInvocationContext banata hai
 * aur ToolInvoker.invoke() ko delegate karta hai - Steps 12-33 ka poora
 * actual enforcement ToolInvoker/security/ratelimit/audit me rehta hai,
 * yahan nahi.
 * Ye kyu hai: Step 3/4/5/6.
 * Dusre components se kaise communicate karta hai: startup par har
 * registry/McpToolDefinition ko registry/ToolRegistry.all() se ek real
 * McpSchema.Tool + McpServerFeatures.SyncToolSpecification me convert
 * karta hai; har tool call registry/ToolInvoker ko delegate hoti hai.
 */
@Configuration
public class McpServerConfig {

    private static final String ROLES_HEADER = "X-Roles";
    private static final String PARTICIPANT_ID_HEADER = "X-Participant-Id";
    private static final String CALLER_ID_KEY = "callerId";
    private static final String ROLES_KEY = "roles";
    private static final String PARTICIPANT_ID_KEY = "participantId";
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String TRACE_ID_KEY = "traceId";

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(new com.fasterxml.jackson.databind.ObjectMapper()))
                .mcpEndpoint("/mcp")
                .contextExtractor(this::extractTransportContext)
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistration(
            HttpServletStreamableServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp");
    }

    @Bean
    public McpSyncServer mcpSyncServer(HttpServletStreamableServerTransportProvider transportProvider,
                                        ToolRegistry toolRegistry, ToolInvoker toolInvoker) {
        McpServer.StreamableSyncSpecification specification = McpServer.sync(transportProvider);
        specification.serverInfo("paymentx-mcp-gateway", "1.0.0");
        specification.instructions("PaymentX MCP Gateway exposes a fixed, read-only catalog of PaymentX operational "
                + "lookup tools. Every call is authorized, validated, rate-limited, and audited. No tool "
                + "in this catalog mutates payment, routing, or participant state.");
        specification.capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build());

        for (PaymentXTool tool : toolRegistry.all()) {
            McpToolDefinition definition = tool.definition();
            McpSchema.Tool mcpTool = McpSchema.Tool.builder()
                    .name(definition.name())
                    .description(definition.description())
                    .inputSchema(definition.inputSchema())
                    .build();
            specification.tool(mcpTool, (exchange, arguments) ->
                    toolInvoker.invoke(definition.name(), arguments, toContext(exchange.transportContext())));
        }

        return specification.build();
    }

    private McpTransportContext extractTransportContext(HttpServletRequest request) {
        Map<String, Object> values = new HashMap<>();
        String rolesHeader = request.getHeader(ROLES_HEADER);
        Set<String> roles = new LinkedHashSet<>();
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            for (String role : rolesHeader.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    roles.add(trimmed);
                }
            }
        }
        values.put(ROLES_KEY, roles);
        String participantId = request.getHeader(PARTICIPANT_ID_HEADER);
        values.put(PARTICIPANT_ID_KEY, participantId);
        values.put(CALLER_ID_KEY, participantId != null ? participantId : "unknown");
        values.put(CORRELATION_ID_KEY, request.getHeader(HeaderConstants.CORRELATION_ID));
        values.put(TRACE_ID_KEY, request.getHeader(HeaderConstants.TRACE_ID));
        return McpTransportContext.create(values);
    }

    @SuppressWarnings("unchecked")
    private ToolInvocationContext toContext(McpTransportContext transportContext) {
        Set<String> roles = (Set<String>) transportContext.get(ROLES_KEY);
        return new ToolInvocationContext(
                roles == null ? Set.of() : roles,
                (String) transportContext.get(PARTICIPANT_ID_KEY),
                (String) transportContext.get(CORRELATION_ID_KEY),
                (String) transportContext.get(TRACE_ID_KEY),
                (String) transportContext.get(CALLER_ID_KEY)
        );
    }
}
