package com.paymentx.mcp.registry;

/**
 * English:
 * Step 20's exact four-value risk classification, required on every
 * tool definition. Every tool implemented in this phase is LOW (plain
 * read of already-public-within-the-platform operational data); MEDIUM/
 * HIGH/CRITICAL exist only so a future write tool (Phase 3.8+, per
 * Step 8/21/22's explicit "do not implement" boundary) has somewhere
 * real to declare itself - payment.retry would be HIGH, payment.refund/
 * payment.cancel would be CRITICAL, matching Step 20's own worked
 * example. Risk level is read by security/ToolAuthorizationService to
 * decide whether write-tool-specific checks apply (Step 20 - "risk
 * level must affect authorization and execution policy").
 * Why it exists: Step 20.
 * How it communicates with other components: one field on
 * registry/McpToolDefinition; read by security/ToolAuthorizationService
 * and audit/McpAuditClient (recorded on every audit event, Step 29).
 *
 * Hinglish:
 * Step 20 ki exact char-value risk classification, jo har tool
 * definition par required hai. Is phase me implement kiya gaya har tool
 * LOW hai (already-public-within-the-platform operational data ka
 * plain read); MEDIUM/HIGH/CRITICAL sirf isliye exist karte hain taaki
 * ek future write tool (Phase 3.8+, Step 8/21/22 ke explicit "implement
 * mat karo" boundary ke hisaab se) ke paas khud ko declare karne ke
 * liye kuch real ho - payment.retry HIGH hota, payment.refund/
 * payment.cancel CRITICAL hote, Step 20 ke apne worked example se match
 * karte hue. Risk level ko security/ToolAuthorizationService padhta hai
 * ye decide karne ke liye ki write-tool-specific checks apply hote hain
 * ya nahi (Step 20 - "risk level ko authorization aur execution policy
 * affect karni chahiye").
 * Ye kyu hai: Step 20.
 * Dusre components se kaise communicate karta hai: registry/
 * McpToolDefinition par ek field; security/ToolAuthorizationService aur
 * audit/McpAuditClient dwara padha jaata hai (har audit event par
 * record hota hai, Step 29).
 */
public enum ToolRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
