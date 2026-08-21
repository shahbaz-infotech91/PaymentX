package com.paymentx.mcp.registry;

/**
 * English:
 * Step 6/22's required read/write classification. WRITE exists purely
 * as a declared value for the type system and for
 * security/ToolAuthorizationService's "WRITE permissions must be
 * separate from READ permissions - never grant WRITE because READ is
 * granted" rule (Step 13/22) to have something real to enforce against
 * if a future phase ever registers a write tool - no PaymentXTool bean
 * in this phase declares WRITE (Step 8/9 - only genuinely safe read-
 * only tools are implemented; every write-capable tool stays disabled
 * by default per Step 22, which in this codebase means "does not exist
 * as a registered bean at all," the strictest possible form of
 * disabled).
 * Why it exists: Step 6/22.
 * How it communicates with other components: one field on
 * registry/McpToolDefinition; security/ToolAuthorizationService checks
 * it before ever considering a WRITE-permission grant.
 *
 * Hinglish:
 * Step 6/22 ki required read/write classification. WRITE sirf type
 * system ke liye aur security/ToolAuthorizationService ke "WRITE
 * permissions READ permissions se alag hone chahiye - kabhi WRITE grant
 * mat karo kyunki READ grant hai" rule (Step 13/22) ke liye ek declared
 * value ke roop me exist karta hai, taaki agar koi future phase kabhi
 * ek write tool register kare toh enforce karne layak kuch real ho - is
 * phase me koi PaymentXTool bean WRITE declare nahi karta (Step 8/9 -
 * sirf genuinely safe read-only tools implement kiye gaye hain; har
 * write-capable tool default se disabled rehta hai Step 22 ke hisaab
 * se, jiska is codebase me matlab hai "ek registered bean ke roop me
 * exist hi nahi karta," disabled ka strictest possible form).
 * Ye kyu hai: Step 6/22.
 * Dusre components se kaise communicate karta hai: registry/
 * McpToolDefinition par ek field; security/ToolAuthorizationService ise
 * kisi bhi WRITE-permission grant consider karne se pehle check karta
 * hai.
 */
public enum ToolReadWrite {
    READ_ONLY,
    WRITE
}
