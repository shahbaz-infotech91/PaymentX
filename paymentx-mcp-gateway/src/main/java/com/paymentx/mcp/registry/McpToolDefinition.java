package com.paymentx.mcp.registry;

import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;

/**
 * English:
 * Step 6's exact required tool-definition shape: name, description,
 * input schema, required permission, risk level, read/write
 * classification, timeout, enabled state, audit classification - one
 * per PaymentXTool implementation. `inputSchema` is the REAL MCP JSON
 * Schema type (io.modelcontextprotocol.spec.McpSchema.JsonSchema) the
 * SDK sends back verbatim on tools/list, so an AI client discovering
 * this tool sees the true, enforced argument shape, never a
 * hand-written description that could drift from what
 * ToolInputValidator actually accepts. `requiredPermission` is one of
 * security/ToolPermissions' constants, checked by
 * security/ToolAuthorizationService before execute() is ever called
 * (Step 12/13). `enabled` defaults to true for every read-only tool
 * registered in this phase - Step 22's "write-capable tools must be
 * DISABLED BY DEFAULT" is satisfied more strongly here: no write tool
 * is registered as a bean at all (see ToolReadWrite's javadoc), so this
 * flag exists for a future write tool to actually use, not as a
 * currently-exercised toggle.
 * Why it exists: Step 6.
 * How it communicates with other components: returned by every
 * PaymentXTool.definition(); registry/ToolRegistry indexes these by
 * name; config/McpServerConfig converts each one into a real MCP
 * McpSchema.Tool + McpServerFeatures.SyncToolSpecification at startup;
 * controller/McpToolCatalogController exposes a redacted view of these
 * (never internal wiring detail) over plain REST for observability.
 *
 * Hinglish:
 * Step 6 ki exact required tool-definition shape: name, description,
 * input schema, required permission, risk level, read/write
 * classification, timeout, enabled state, audit classification - har
 * PaymentXTool implementation ke liye ek. `inputSchema` REAL MCP JSON
 * Schema type hai (io.modelcontextprotocol.spec.McpSchema.JsonSchema)
 * jise SDK tools/list par verbatim wapas bhejta hai, taaki is tool ko
 * discover karne wala ek AI client true, enforced argument shape dekhe,
 * kabhi ek hand-written description nahi jo ToolInputValidator jo
 * actually accept karta hai usse drift kar sake. `requiredPermission`
 * security/ToolPermissions ke constants me se ek hai, jise
 * security/ToolAuthorizationService execute() call hone se pehle check
 * karta hai (Step 12/13). `enabled` is phase me registered har read-
 * only tool ke liye default true hai - Step 22 ka "write-capable tools
 * default se DISABLED hone chahiye" yahan aur strongly satisfy hota hai:
 * koi write tool bean ke roop me register hi nahi hota (ToolReadWrite
 * ka javadoc dekho), isliye ye flag ek future write tool ke actually
 * use karne ke liye exist karta hai, ek currently-exercised toggle ke
 * roop me nahi.
 * Ye kyu hai: Step 6.
 * Dusre components se kaise communicate karta hai: har
 * PaymentXTool.definition() dwara return hota hai; registry/ToolRegistry
 * inhe naam se index karta hai; config/McpServerConfig startup par har
 * ek ko ek real MCP McpSchema.Tool + McpServerFeatures.SyncToolSpecification
 * me convert karta hai; controller/McpToolCatalogController inka ek
 * redacted view (kabhi internal wiring detail nahi) plain REST par
 * observability ke liye expose karta hai.
 */
public record McpToolDefinition(
        String name,
        String description,
        McpSchema.JsonSchema inputSchema,
        String requiredPermission,
        ToolRiskLevel riskLevel,
        ToolReadWrite readWrite,
        Duration timeout,
        boolean enabled,
        String auditClassification
) {
}
