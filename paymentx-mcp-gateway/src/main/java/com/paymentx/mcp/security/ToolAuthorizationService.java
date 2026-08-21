package com.paymentx.mcp.security;

import com.paymentx.mcp.exception.McpException;
import com.paymentx.mcp.registry.McpToolDefinition;
import com.paymentx.mcp.registry.ToolInvocationContext;
import com.paymentx.mcp.registry.ToolReadWrite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * English:
 * The real, only place Step 12's "every tool invocation must pass
 * authorization... AI is NOT trusted" is enforced. Two independent
 * checks:
 * 1. checkPermission - the caller's real X-Roles (registry/
 *    ToolInvocationContext) must contain the tool's
 *    definition().requiredPermission(), or the call is denied before
 *    the tool's execute() is ever invoked (TOOL_UNAUTHORIZED if the
 *    caller has NO roles at all - could not even authenticate - vs.
 *    TOOL_FORBIDDEN if the caller has roles but not this one, matching
 *    the conventional 401-vs-403 distinction). A WRITE-classified tool
 *    additionally always throws WRITE_OPERATION_NOT_ALLOWED regardless
 *    of role (Step 22 - write tools are never enabled by permission
 *    alone in this phase; no such tool is even registered today, but
 *    this guard exists so the rule is enforced in code, not just by
 *    omission, if one ever is).
 * 2. checkResourceOwnership (Step 14) - a caller scoped to one
 *    participant (a non-null X-Participant-Id) must not see another
 *    participant's resource. Called by individual tools (tool/
 *    PaymentLookupTool, tool/RoutingLookupTool, tool/AuditSearchTool)
 *    AFTER they know which participant a resource actually belongs to
 *    - this service does not (and structurally cannot) know that in
 *    advance, since it is real business data, not something the
 *    security layer owns. A caller with no X-Participant-Id (an
 *    operator/platform-level caller, matching this platform's existing
 *    convention elsewhere - e.g. ReconciliationController's
 *    triggeredBy) is not resource-scoped at all and passes this check
 *    unconditionally - this is an honest limitation of the real,
 *    already-existing PaymentX identity model (see
 *    PAYMENTX_PHASE_3_ARCHITECTURE.md §1's "no real platform-wide
 *    identity system yet" finding), not something this phase can
 *    invent a stronger guarantee for.
 * Why it exists: Step 12/13/14/20/22.
 * How it communicates with other components: called by ToolInvoker
 * before every execute(); called directly by individual tool/ classes
 * for resource-level checks once they have fetched real data.
 *
 * Hinglish:
 * Ye woh real, ek hi jagah hai jahan Step 12 ka "har tool invocation ko
 * authorization pass karna hoga... AI TRUSTED NAHI hai" enforce hota
 * hai. Do independent checks:
 * 1. checkPermission - caller ke real X-Roles (registry/
 *    ToolInvocationContext) me tool ka definition().requiredPermission()
 *    hona chahiye, warna call deny ho jaati hai tool ka execute() kabhi
 *    invoke hone se pehle (TOOL_UNAUTHORIZED agar caller ke paas KOI
 *    roles hi nahi hain - authenticate hi nahi ho paya - vs.
 *    TOOL_FORBIDDEN agar caller ke paas roles hain lekin ye wala nahi,
 *    conventional 401-vs-403 distinction se match karte hue). Ek
 *    WRITE-classified tool additionally hamesha WRITE_OPERATION_NOT_ALLOWED
 *    throw karta hai role ke bawajood (Step 22 - write tools is phase
 *    me kabhi sirf permission se enable nahi hote; aisa koi tool aaj
 *    register hi nahi hai, lekin ye guard isliye exist karta hai taaki
 *    rule code me enforce ho, sirf omission se nahi, agar kabhi ek
 *    register ho).
 * 2. checkResourceOwnership (Step 14) - ek caller jo ek participant tak
 *    scoped hai (ek non-null X-Participant-Id) use ek doosre
 *    participant ka resource nahi dikhna chahiye. Individual tools
 *    (tool/PaymentLookupTool, tool/RoutingLookupTool, tool/AuditSearchTool)
 *    dwara call hota hai UNKE ye jaanne ke BAAD ki ek resource actually
 *    kis participant ka hai - ye service ye pehle se nahi jaanti (aur
 *    structurally jaan hi nahi sakti), kyunki ye real business data
 *    hai, kuch aisa nahi jo security layer khud owns karti ho. Ek
 *    caller jiske paas X-Participant-Id hi nahi hai (ek operator/
 *    platform-level caller, is platform ke existing convention se match
 *    karte hue - jaise ReconciliationController ka triggeredBy)
 *    bilkul resource-scoped nahi hai aur ye check unconditionally pass
 *    kar jaata hai - ye real, already-existing PaymentX identity model
 *    ki ek honest limitation hai (PAYMENTX_PHASE_3_ARCHITECTURE.md §1
 *    ka "abhi koi real platform-wide identity system nahi hai" finding
 *    dekho), kuch aisa nahi jiske liye ye phase ek stronger guarantee
 *    invent kar sake.
 * Ye kyu hai: Step 12/13/14/20/22.
 * Dusre components se kaise communicate karta hai: ToolInvoker dwara har
 * execute() se pehle call hota hai; individual tool/ classes dwara
 * seedhe resource-level checks ke liye call hota hai unke real data
 * fetch karne ke baad.
 */
@Component
@Slf4j
public class ToolAuthorizationService {

    public void checkPermission(ToolInvocationContext context, McpToolDefinition definition) {
        if (definition.readWrite() == ToolReadWrite.WRITE) {
            log.warn("Denied WRITE tool call toolName={} callerId={}", definition.name(), context.callerId());
            throw McpException.writeOperationNotAllowed(definition.name());
        }

        if (context.roles() == null || context.roles().isEmpty()) {
            throw McpException.unauthorized("No authenticated roles were presented for tool: " + definition.name());
        }

        if (!context.hasRole(definition.requiredPermission())) {
            log.warn("Denied tool call toolName={} requiredPermission={} callerId={}",
                    definition.name(), definition.requiredPermission(), context.callerId());
            throw McpException.forbidden("Caller lacks required permission " + definition.requiredPermission()
                    + " for tool: " + definition.name());
        }
    }

    /**
     * Step 14 - resource-boundary enforcement. Pass the real owning
     * participant id(s) of the resource just fetched; null/blank means
     * "this resource carries no participant scoping in its own data" and
     * always passes.
     */
    public void checkResourceOwnership(ToolInvocationContext context, String toolName, String... resourceParticipantIds) {
        if (context.participantId() == null || context.participantId().isBlank()) {
            return;
        }
        for (String resourceParticipantId : resourceParticipantIds) {
            if (Objects.equals(context.participantId(), resourceParticipantId)) {
                return;
            }
        }
        log.warn("Denied cross-participant resource access toolName={} callerParticipantId={}", toolName, context.participantId());
        throw McpException.resourceForbidden("Caller participant " + context.participantId()
                + " is not authorized to view this resource via tool: " + toolName);
    }
}
