package com.paymentx.mcp.registry;

import java.util.Map;

/**
 * English:
 * The one interface every real MCP tool in this gateway implements -
 * explicit, hand-written, Spring-@Component-registered classes only
 * (Step 5 - "Do NOT dynamically load arbitrary Java classes based on
 * AI-provided input. Tool registration must be explicit."). There is no
 * reflection, no classpath scanning for a "tool name" string, no plugin
 * loader - registry/ToolRegistry simply collects every Spring bean that
 * implements this interface at startup (a fixed, compile-time-known
 * list), the same "fixed allowlist, never a client-supplied anything"
 * pattern Control Center's ApiTesterAllowlist already established for
 * exactly this kind of attack surface (see
 * PAYMENTX_PHASE_3_ARCHITECTURE.md §10). execute() receives ONLY the
 * arguments the MCP client actually sent (already schema/shape-checked
 * by the SDK against definition().inputSchema() before this is ever
 * called) plus the real caller identity - it must perform its OWN
 * business-specific input validation (paymentReference format,
 * pagination bounds, etc, Step 15) and its OWN resource-level
 * authorization where the underlying data makes that possible (Step
 * 14), on top of the tool-level authorization ToolInvoker already
 * performed before calling execute() at all. Must never touch a
 * database directly (Step 11) - every real implementation in this
 * phase calls a client/ class that in turn calls a real PaymentX
 * business-service REST API.
 * Why it exists: Step 5/6/11/15.
 * How it communicates with other components: implemented by every class
 * in tool/; collected by registry/ToolRegistry; invoked by ToolInvoker
 * after authorization/rate-limiting have already passed.
 *
 * Hinglish:
 * Ye is gateway ka ek hi interface hai jise har real MCP tool implement
 * karta hai - sirf explicit, hand-written, Spring-@Component-registered
 * classes (Step 5 - "AI-provided input ke aadhar par arbitrary Java
 * classes dynamically load MAT karo. Tool registration explicit hona
 * chahiye."). Koi reflection nahi, koi classpath scanning ek "tool
 * name" string ke liye nahi, koi plugin loader nahi - registry/
 * ToolRegistry startup par bas har us Spring bean ko collect karta hai
 * jo ye interface implement karta hai (ek fixed, compile-time-known
 * list), exactly wahi "fixed allowlist, kabhi ek client-supplied kuch
 * bhi nahi" pattern jo Control Center ka ApiTesterAllowlist isi tarah
 * ke attack surface ke liye already establish kar chuka hai
 * (PAYMENTX_PHASE_3_ARCHITECTURE.md §10 dekho). execute() ko SIRF wahi
 * arguments milte hain jo MCP client ne actually bheje (SDK dwara
 * definition().inputSchema() ke against pehle se hi schema/shape-checked,
 * isse pehle ki ye kabhi call ho) plus real caller identity - use apna
 * KHUD ka business-specific input validation (paymentReference format,
 * pagination bounds, etc, Step 15) aur apna KHUD ka resource-level
 * authorization (jahan underlying data se ye possible ho, Step 14)
 * perform karna hota hai, us tool-level authorization ke upar jo
 * ToolInvoker execute() ko call karne se pehle hi perform kar chuka
 * hota hai. Kabhi ek database seedhe touch nahi karna chahiye (Step
 * 11) - is phase ka har real implementation ek client/ class call karta
 * hai jo aage ek real PaymentX business-service REST API call karti
 * hai.
 * Ye kyu hai: Step 5/6/11/15.
 * Dusre components se kaise communicate karta hai: tool/ ki har class
 * dwara implement kiya jaata hai; registry/ToolRegistry dwara collect
 * hota hai; ToolInvoker dwara invoke hota hai, authorization/rate-
 * limiting pehle se pass hone ke baad.
 */
public interface PaymentXTool {

    McpToolDefinition definition();

    Map<String, Object> execute(ToolInvocationContext context, Map<String, Object> arguments);
}
