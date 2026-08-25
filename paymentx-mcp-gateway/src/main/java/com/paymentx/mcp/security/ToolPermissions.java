package com.paymentx.mcp.security;

/**
 * English:
 * Step 13's exact required permission constants - each one is checked
 * against the caller's real X-Roles (see registry/ToolInvocationContext's
 * javadoc for where those come from) by ToolAuthorizationService, never
 * granted implicitly. The five *_READ constants are the ones actually
 * wired to a real tool in this phase (see tool/'s five classes). The two
 * *_READ constants left undeclared here on purpose - a participant-
 * lookup permission and an error-code-lookup permission - are not
 * defined at all, because Step 9/10/39 requires participant.lookup and
 * payment.error.lookup to be documented as NOT AVAILABLE rather than
 * backed by a workaround (see PAYMENTX_PHASE_3_7_MCP_GATEWAY.md's Tool
 * List section) - defining a permission constant for a tool that will
 * never exist would be dead, misleading code. The three WRITE
 * constants are declared, per Step 13's own explicit instruction
 * ("Future examples: PAYMENT_RETRY, PAYMENT_CANCEL, PAYMENT_REFUND"),
 * but are NEVER referenced by any McpToolDefinition, NEVER checked by
 * ToolAuthorizationService, and NEVER granted to any caller anywhere in
 * this codebase - they exist only so a future write tool (Phase 3.8+)
 * has a real permission name to require, matching Step 13's "WRITE
 * permissions must be separate from READ permissions. Never grant
 * WRITE because READ is granted."
 * Why it exists: Step 13.
 * How it communicates with other components: referenced as the
 * requiredPermission value on registry/McpToolDefinition instances built
 * by every real tool/ class; checked by ToolAuthorizationService.checkPermission.
 *
 * Hinglish:
 * Step 13 ke exact required permission constants - har ek ko caller ke
 * real X-Roles ke against check kiya jaata hai (ye kahan se aate hain
 * ke liye registry/ToolInvocationContext ka javadoc dekho)
 * ToolAuthorizationService dwara, kabhi implicitly grant nahi hote.
 * Paanch *_READ constants wahi hain jo is phase me ek real tool se
 * actually wire hue hain (tool/ ki paanch classes dekho). Do *_READ
 * constants jaan-boojh kar yahan undeclared chhode gaye hain - ek
 * participant-lookup permission aur ek error-code-lookup permission -
 * bilkul define nahi kiye gaye, kyunki Step 9/10/39 ko
 * participant.lookup aur payment.error.lookup ko NOT AVAILABLE ke roop
 * me document karna hota hai, ek workaround se backed karne ke bajaye
 * (PAYMENTX_PHASE_3_7_MCP_GATEWAY.md ka Tool List section dekho) - ek
 * tool ke liye ek permission constant define karna jo kabhi exist hi
 * nahi karega, dead, misleading code hota. Teen WRITE constants declare
 * kiye gaye hain, Step 13 ke apne explicit instruction ke hisaab se
 * ("Future examples: PAYMENT_RETRY, PAYMENT_CANCEL, PAYMENT_REFUND"),
 * lekin kisi bhi McpToolDefinition se KABHI reference nahi hote,
 * ToolAuthorizationService se KABHI check nahi hote, aur is codebase me
 * kahin bhi kisi caller ko KABHI grant nahi hote - ye sirf isliye exist
 * karte hain taaki ek future write tool (Phase 3.8+) ke paas require
 * karne ke liye ek real permission name ho, Step 13 ke "WRITE
 * permissions READ permissions se alag hone chahiye. Kabhi WRITE grant
 * mat karo kyunki READ grant hai" se match karte hue.
 * Ye kyu hai: Step 13.
 * Dusre components se kaise communicate karta hai: har real tool/
 * class dwara banaye gaye registry/McpToolDefinition instances par
 * requiredPermission value ke roop me reference hota hai;
 * ToolAuthorizationService.checkPermission dwara check hota hai.
 */
public final class ToolPermissions {

    public static final String PAYMENT_READ = "PAYMENT_READ";
    public static final String ROUTING_READ = "ROUTING_READ";
    public static final String RECONCILIATION_READ = "RECONCILIATION_READ";
    public static final String AUDIT_READ = "AUDIT_READ";
    /** Phase 4.4 - backs database.statistics, wired to Control Center's existing read-only Postgres API. */
    public static final String DATABASE_READ = "DATABASE_READ";

    /** Declared per Step 13's own instruction; never checked, never granted - see class javadoc. */
    public static final String PAYMENT_RETRY = "PAYMENT_RETRY";
    public static final String PAYMENT_CANCEL = "PAYMENT_CANCEL";
    public static final String PAYMENT_REFUND = "PAYMENT_REFUND";

    private ToolPermissions() {
    }
}
