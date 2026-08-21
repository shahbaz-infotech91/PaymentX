package com.paymentx.mcp.dto;

/**
 * English:
 * The redacted, read-only view of a registry/McpToolDefinition exposed
 * by controller/McpToolCatalogController - deliberately excludes
 * inputSchema's raw internal representation and any wiring detail, in
 * line with Step 36's "do NOT build a full MCP management UI... a
 * future Control Center page may show available tools" - this is that
 * page's eventual data source, not a management surface (no way to
 * enable/disable/invoke a tool through this endpoint).
 * Why it exists: Step 5 ("expose safe tool descriptions") + Step 36.
 * How it communicates with other components: built by
 * controller/McpToolCatalogController from registry/ToolRegistry.all().
 *
 * Hinglish:
 * Ye ek registry/McpToolDefinition ka redacted, read-only view hai jo
 * controller/McpToolCatalogController expose karta hai - jaan-boojh kar
 * inputSchema ki raw internal representation aur koi bhi wiring detail
 * exclude karta hai, Step 36 ke "ek poori MCP management UI mat banao...
 * ek future Control Center page available tools dikha sakta hai" ke
 * hisaab se - ye us page ka eventual data source hai, ek management
 * surface nahi (is endpoint se koi tool enable/disable/invoke karne ka
 * koi tareeka nahi).
 * Ye kyu hai: Step 5 ("safe tool descriptions expose karo") + Step 36.
 * Dusre components se kaise communicate karta hai: controller/
 * McpToolCatalogController dwara registry/ToolRegistry.all() se banaya
 * jaata hai.
 */
public record ToolDescriptorResponse(
        String name,
        String description,
        String requiredPermission,
        String riskLevel,
        String readWrite,
        long timeoutMillis,
        boolean enabled
) {
}
