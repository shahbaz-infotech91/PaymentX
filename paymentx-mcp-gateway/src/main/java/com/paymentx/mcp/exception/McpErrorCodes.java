package com.paymentx.mcp.exception;

/**
 * English:
 * Step 32's exact required normalized error-code list - every real
 * failure this gateway can produce, thrown consistently as one of these
 * codes via McpException. Deliberately does NOT include a distinct
 * "business not found" code (e.g. PAYMENT_NOT_FOUND) - Step 33 requires
 * distinguishing an MCP infrastructure error from a real PaymentX
 * business result, and a downstream service correctly reporting
 * "no such payment" is a legitimate tool RESULT (isError=false,
 * structured content with found=false), never a thrown McpException -
 * see tool/PaymentLookupTool's javadoc.
 * Why it exists: Step 32.
 * How it communicates with other components: every constant here is a
 * McpException static factory's errorCode; ToolInvoker/registry/security/
 * client classes throw these; the plain REST controller's
 * GlobalExceptionHandler renders them into ApiResponse&lt;Void&gt; error
 * bodies for the one non-MCP endpoint this service exposes.
 *
 * Hinglish:
 * Step 32 ki exact required normalized error-code list - is gateway ka
 * har real failure jo produce ho sakta hai, McpException ke through in
 * codes me se ek ke roop me consistently throw hota hai. Jaan-boojh kar
 * ek alag "business not found" code (jaise PAYMENT_NOT_FOUND) shamil
 * NAHI karta - Step 33 ko ek MCP infrastructure error ko ek real
 * PaymentX business result se distinguish karna hota hai, aur ek
 * downstream service ka sahi se "aisa koi payment nahi hai" report
 * karna ek legitimate tool RESULT hai (isError=false, structured
 * content found=false ke saath), kabhi ek thrown McpException nahi -
 * tool/PaymentLookupTool ka javadoc dekho.
 * Ye kyu hai: Step 32.
 * Dusre components se kaise communicate karta hai: yahan har constant
 * ek McpException static factory ka errorCode hai; ToolInvoker/
 * registry/security/client classes inhe throw karte hain; is service ke
 * ek hi non-MCP endpoint ka GlobalExceptionHandler inhe
 * ApiResponse&lt;Void&gt; error bodies me render karta hai.
 */
public final class McpErrorCodes {

    public static final String TOOL_NOT_FOUND = "TOOL_NOT_FOUND";
    public static final String TOOL_DISABLED = "TOOL_DISABLED";
    public static final String INVALID_TOOL_ARGUMENTS = "INVALID_TOOL_ARGUMENTS";
    public static final String TOOL_UNAUTHORIZED = "TOOL_UNAUTHORIZED";
    public static final String TOOL_FORBIDDEN = "TOOL_FORBIDDEN";
    public static final String RESOURCE_FORBIDDEN = "RESOURCE_FORBIDDEN";
    public static final String TOOL_TIMEOUT = "TOOL_TIMEOUT";
    public static final String TOOL_RATE_LIMITED = "TOOL_RATE_LIMITED";
    public static final String TARGET_SERVICE_UNAVAILABLE = "TARGET_SERVICE_UNAVAILABLE";
    public static final String TOOL_EXECUTION_FAILED = "TOOL_EXECUTION_FAILED";
    public static final String WRITE_OPERATION_NOT_ALLOWED = "WRITE_OPERATION_NOT_ALLOWED";
    public static final String APPROVAL_REQUIRED = "APPROVAL_REQUIRED";

    private McpErrorCodes() {
    }
}
