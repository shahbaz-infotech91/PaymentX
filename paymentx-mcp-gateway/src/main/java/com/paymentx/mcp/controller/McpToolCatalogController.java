package com.paymentx.mcp.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.mcp.dto.ToolDescriptorResponse;
import com.paymentx.mcp.registry.ToolRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * English:
 * The ONLY plain @RestController endpoint in this module -
 * GET /api/v1/mcp/tools, a redacted, read-only listing of the fixed
 * tool catalog (Step 5/36). Real AI clients discover tools through the
 * actual MCP protocol's tools/list method (config/McpServerConfig,
 * /mcp) - this REST endpoint exists purely for human/operational
 * observability (matching every other PaymentX service's real REST-
 * surface-plus-actuator-health convention) and as the eventual data
 * source for a future Control Center "AI Tools" page (Step 36 - "may
 * show available tools" - not built in this phase). No tool
 * administration (enable/disable/invoke) is possible through this
 * endpoint.
 * Why it exists: Step 5/36.
 * How it communicates with other components: reads registry/
 * ToolRegistry.all() directly - never touches a client/ class or
 * ToolInvoker, since it only describes tools, never calls them.
 *
 * Hinglish:
 * Ye is module ka EK hi plain @RestController endpoint hai -
 * GET /api/v1/mcp/tools, fixed tool catalog ka ek redacted, read-only
 * listing (Step 5/36). Real AI clients tools ko actual MCP protocol ke
 * tools/list method se discover karte hain (config/McpServerConfig,
 * /mcp) - ye REST endpoint sirf human/operational observability ke liye
 * exist karta hai (har doosri PaymentX service ke real REST-surface-
 * plus-actuator-health convention se match karte hue) aur ek future
 * Control Center "AI Tools" page ke eventual data source ke roop me
 * (Step 36 - "available tools dikha sakta hai" - is phase me nahi
 * banaya gaya). Is endpoint se koi tool administration (enable/disable/
 * invoke) possible nahi hai.
 * Ye kyu hai: Step 5/36.
 * Dusre components se kaise communicate karta hai: seedhe registry/
 * ToolRegistry.all() padhta hai - kabhi ek client/ class ya ToolInvoker
 * ko touch nahi karta, kyunki ye sirf tools describe karta hai, kabhi
 * unhe call nahi karta.
 */
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP Gateway", description = "Read-only visibility into the fixed MCP tool catalog")
public class McpToolCatalogController {

    private final ToolRegistry toolRegistry;

    @GetMapping("/tools")
    @Operation(summary = "List the fixed MCP tool catalog", description = "Redacted, read-only tool descriptors - never invokes a tool.")
    public ResponseEntity<ApiResponse<List<ToolDescriptorResponse>>> listTools() {
        List<ToolDescriptorResponse> tools = toolRegistry.all().stream()
                .map(tool -> new ToolDescriptorResponse(
                        tool.definition().name(),
                        tool.definition().description(),
                        tool.definition().requiredPermission(),
                        tool.definition().riskLevel().name(),
                        tool.definition().readWrite().name(),
                        tool.definition().timeout().toMillis(),
                        tool.definition().enabled()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(tools));
    }
}
