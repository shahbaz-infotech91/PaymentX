package com.paymentx.mcp.registry;

import java.util.Set;

/**
 * English:
 * The real caller identity every tool call carries - extracted directly
 * from the raw HTTP request's already-trusted X-Roles/X-Participant-Id
 * headers by config/McpServerConfig's transport-context extractor (the
 * exact same headers HeaderRoleAuthenticationFilter reads elsewhere in
 * this platform - see that config class's javadoc for why a raw header
 * read, not Spring Security's SecurityContextHolder, is used here).
 * `roles` is never empty-checked for "is this caller trusted" - Step 12
 * is explicit that AI is NOT trusted by default, so
 * security/ToolAuthorizationService treats an empty roles set as "no
 * permissions granted," never as "internal caller, allow everything."
 * `participantId`, when present, is what security/ToolAuthorizationService
 * uses for Step 14's resource-boundary check (a caller scoped to one
 * participant must not see another participant's payment/routing data).
 * Why it exists: Step 12/13/14/29 all need real caller identity at tool-
 * execution time, not just at the HTTP-transport layer.
 * How it communicates with other components: built once per MCP request
 * by the transport-context extractor in config/McpServerConfig; passed
 * through ToolInvoker to security/ToolAuthorizationService,
 * ratelimit/ToolCallRateLimiter, audit/McpAuditClient, and every
 * PaymentXTool.execute(...) call.
 *
 * Hinglish:
 * Har tool call jo real caller identity carry karta hai - raw HTTP
 * request ke already-trusted X-Roles/X-Participant-Id headers se seedhe
 * extract kiya gaya, config/McpServerConfig ke transport-context
 * extractor dwara (exactly wahi headers jo HeaderRoleAuthenticationFilter
 * is platform me kahin aur padhta hai - us config class ka javadoc
 * dekho ki yahan ek raw header read, Spring Security ka
 * SecurityContextHolder nahi, kyu use kiya gaya). `roles` ko kabhi "kya
 * ye caller trusted hai" ke liye empty-check nahi kiya jaata - Step 12
 * explicit hai ki AI default se TRUSTED NAHI hai, isliye security/
 * ToolAuthorizationService ek empty roles set ko "koi permissions grant
 * nahi hue" maanta hai, "internal caller, sab allow karo" nahi.
 * `participantId`, jab present ho, wo hai jise security/
 * ToolAuthorizationService Step 14 ke resource-boundary check ke liye
 * use karta hai (ek caller jo ek participant tak scoped hai use ek
 * doosre participant ka payment/routing data nahi dikhna chahiye).
 * Ye kyu hai: Step 12/13/14/29 sabko tool-execution time par real
 * caller identity chahiye, sirf HTTP-transport layer par nahi.
 * Dusre components se kaise communicate karta hai: har MCP request ke
 * liye ek baar config/McpServerConfig ke transport-context extractor
 * dwara banaya jaata hai; ToolInvoker ke through security/
 * ToolAuthorizationService, ratelimit/ToolCallRateLimiter, audit/
 * McpAuditClient, aur har PaymentXTool.execute(...) call tak pass hota
 * hai.
 */
public record ToolInvocationContext(
        Set<String> roles,
        String participantId,
        String correlationId,
        String traceId,
        String callerId
) {
    public boolean hasRole(String permission) {
        return roles != null && roles.contains(permission);
    }
}
