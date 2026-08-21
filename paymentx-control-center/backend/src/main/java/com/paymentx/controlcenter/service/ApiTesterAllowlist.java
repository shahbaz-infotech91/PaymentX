package com.paymentx.controlcenter.service;

import com.paymentx.controlcenter.dto.apitester.ApiTesterEndpointDescriptor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * ENGLISH: THE entire SSRF/attack-surface boundary for the Phase 5 API
 * Tester - a fixed, hardcoded, real list of {service, method,
 * pathTemplate} triples, each one copied from an endpoint that
 * genuinely exists in the corresponding service's real
 * @RestController (verified live against source, not guessed or
 * invented). What it does: match() is the ONLY way ApiTesterService
 * ever decides whether to make an outbound call - a request whose
 * (service, method, path) doesn't exactly match one compiled template
 * here is rejected before any HTTP client is even touched. Why it
 * exists: the Phase 5 brief's explicit "MUST NOT allow arbitrary URLs
 * - create an explicit allowlist" requirement; validation-service and
 * payment-service's endpoints are deliberately routed through
 * "api-gateway" (matching how real production traffic actually reaches
 * them - see infra recon: only those two have a real Spring Cloud
 * Gateway route), while routing/audit/notification/reconciliation/
 * reporting are called on their own real, direct ports (they have no
 * gateway route). DELETE is listed for exactly the two real DELETE
 * endpoints this platform exposes (routing rule delete, report
 * execution delete) - "DELETE only where explicitly allowed" IS this
 * allowlist. Multipart-upload and binary-download endpoints are
 * deliberately excluded - out of scope for a JSON request/response
 * tester, not a security concern.
 *
 * HINGLISH: Phase 5 API Tester ke liye POORI SSRF/attack-surface
 * boundary - {service, method, pathTemplate} triples ki ek fixed,
 * hardcoded, real list, har ek us endpoint se copy kiya gaya jo
 * corresponding service ke real @RestController me genuinely exist
 * karta hai (actual source ke against live verify kiya gaya, guess ya
 * invent nahi kiya gaya). Ye kya karti hai: match() hi wo EK tareeka
 * hai jisse ApiTesterService kabhi decide karta hai ki outbound call
 * banani hai ya nahi - ek request jiska (service, method, path) yahan
 * kisi compiled template se exactly match na kare, kisi HTTP client ko
 * touch kiye bina hi reject ho jaata hai. Ye dashboard me kyu hai:
 * Phase 5 brief ka explicit "arbitrary URLs allow MAT karo - ek
 * explicit allowlist banao" requirement; validation-service aur
 * payment-service ke endpoints jaan-boojh kar "api-gateway" ke through
 * route kiye gaye hain (matching ki real production traffic unhe
 * actually kaise pahunchta hai - infra recon dekho: sirf inhi do ka
 * real Spring Cloud Gateway route hai), jabki
 * routing/audit/notification/reconciliation/reporting apne real,
 * direct ports par call kiye jaate hain (unka koi gateway route nahi
 * hai). DELETE exactly un do real DELETE endpoints ke liye listed hai
 * jo ye platform expose karta hai (routing rule delete, report
 * execution delete) - "DELETE sirf jahan explicitly allowed ho" YEHI
 * allowlist hai. Multipart-upload aur binary-download endpoints
 * jaan-boojh kar exclude kiye gaye hain - ek JSON request/response
 * tester ke liye out of scope, koi security concern nahi.
 */
@Component
public class ApiTesterAllowlist {

    private record Entry(ApiTesterEndpointDescriptor descriptor, Pattern pathPattern) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public ApiTesterAllowlist() {
        // --- Health checks - every real service, including the two with no other endpoints allowlisted. ---
        for (String service : List.of("api-gateway", "auth-service", "validation-service", "payment-service",
                "routing-service", "audit-service", "notification-service", "reconciliation-service", "reporting-service")) {
            add(service, "GET", "/actuator/health", "Real Spring Boot Actuator health check.", false);
        }

        // --- Validation-service + Payment-service, via the real Gateway route (this is how real traffic reaches them). ---
        add("api-gateway", "POST", "/api/v1/validations",
                "Submit a real payment for validation - the real entry point to the whole payment flow (requires a real X-Api-Key or JWT).", false);
        add("api-gateway", "GET", "/api/v1/payments/{reference}", "Full current snapshot of one real payment.", false);
        add("api-gateway", "GET", "/api/v1/payments/{reference}/status", "Lightweight status-only read, for polling.", false);
        add("api-gateway", "GET", "/api/v1/payments", "Paginated payment listing (query: status, page, size).", false);
        add("api-gateway", "GET", "/api/v1/payments/search", "Multi-criteria payment search (query: status, scheme, participantId, createdFrom, createdTo, minAmount, maxAmount, page, size).", false);
        add("api-gateway", "POST", "/api/v1/payments/{reference}/retry", "Seed a retry record for a failed payment.", false);
        add("api-gateway", "POST", "/api/v1/payments/{reference}/cancel", "Cancel a payment before funds have moved.", false);

        // --- Routing-service - no gateway route, called directly on its real port. ---
        add("routing-service", "GET", "/api/v1/routes", "Search/list routing rules (query: scheme, participantId, active, isDefault, page, size).", false);
        add("routing-service", "GET", "/api/v1/routes/{id}", "Get one routing rule by id.", false);
        add("routing-service", "GET", "/api/v1/routes/default", "Get the default rule for a scheme (query: scheme).", false);
        add("routing-service", "GET", "/api/v1/routes/participant/{participantId}", "Resolve the active route for a participant+scheme (query: scheme).", false);
        add("routing-service", "POST", "/api/v1/routes", "Create a routing rule (requires ROUTING_ADMIN role).", false);
        add("routing-service", "PUT", "/api/v1/routes/{id}", "Update a routing rule (requires ROUTING_ADMIN role).", false);
        add("routing-service", "DELETE", "/api/v1/routes/{id}", "Permanently delete a routing rule (requires ROUTING_ADMIN role).", true);

        // --- Audit-service - no gateway route. Only real read endpoints allowlisted (the direct-write POST bypasses the normal Kafka audit path and is low-value here). ---
        add("audit-service", "GET", "/api/v1/audit-events", "Search the audit trail (query: correlationId, paymentId, participantId, reference, status, eventType, fromDate, toDate, page, size).", false);
        add("audit-service", "GET", "/api/v1/audit-events/{id}", "Get one audit event by id.", false);

        // --- Notification-service - no gateway route. ---
        add("notification-service", "GET", "/api/v1/notifications", "Search notifications (query: status, channel, sourceEventType, recipient, participantId, fromDate, toDate, page, size).", false);
        add("notification-service", "GET", "/api/v1/notifications/{id}", "Get one notification by id.", false);
        add("notification-service", "GET", "/api/v1/notifications/{id}/history", "Full delivery-attempt history for one notification.", false);
        add("notification-service", "POST", "/api/v1/notifications/{id}/retry", "Retry a failed/dead-lettered notification (requires NOTIFICATION_ADMIN role).", false);
        add("notification-service", "POST", "/api/v1/notifications/{id}/resend", "Resend as a new, separately-tracked send (requires NOTIFICATION_ADMIN role).", false);

        // --- Reconciliation-service - no gateway route. Multipart file upload and binary CSV report download excluded (out of scope for a JSON tester). ---
        add("reconciliation-service", "GET", "/api/v1/reconciliation/batches/{batchId}", "Get one reconciliation batch's status.", false);
        add("reconciliation-service", "GET", "/api/v1/reconciliation/batches/{batchId}/summary", "Aggregate summary of a completed batch.", false);
        add("reconciliation-service", "GET", "/api/v1/reconciliation/mismatches", "Search mismatch records (query: batchId, mismatchType, resolved, page, size).", false);
        add("reconciliation-service", "POST", "/api/v1/reconciliation/batches", "Start a reconciliation batch (requires RECONCILIATION_ADMIN role; has real settlement processing side effects).", false);
        add("reconciliation-service", "POST", "/api/v1/reconciliation/batches/{batchId}/reprocess", "Reprocess a batch (requires RECONCILIATION_ADMIN role).", false);
        add("reconciliation-service", "POST", "/api/v1/reconciliation/mismatches/{mismatchId}/resolve", "Mark a mismatch as resolved (query: notes; requires RECONCILIATION_ADMIN role).", false);

        // --- Reporting-service - no gateway route. Binary report download excluded (out of scope for a JSON tester). ---
        add("reporting-service", "GET", "/api/v1/reports", "List all supported report types.", false);
        add("reporting-service", "GET", "/api/v1/reports/executions/{executionId}", "Get one report execution's status.", false);
        add("reporting-service", "GET", "/api/v1/reports/executions/{executionId}/result", "View a completed report's real result data.", false);
        add("reporting-service", "GET", "/api/v1/reports/executions", "Search report requests/executions (query: reportType, participantId, currency, correlationId, fromDate, toDate, page, size).", false);
        add("reporting-service", "POST", "/api/v1/reports/generate", "Generate a report asynchronously (requires REPORTING_ADMIN role; returns 202).", false);
        add("reporting-service", "POST", "/api/v1/reports/schedules", "Schedule a recurring report (requires REPORTING_ADMIN role).", false);
        add("reporting-service", "POST", "/api/v1/reports/executions/{executionId}/cancel", "Cancel a pending/running report execution (requires REPORTING_ADMIN role).", false);
        add("reporting-service", "DELETE", "/api/v1/reports/executions/{executionId}", "Permanently delete a report execution (requires REPORTING_ADMIN role).", true);
    }

    private void add(String service, String method, String pathTemplate, String description, boolean destructive) {
        String regex = "^" + pathTemplate.replaceAll("\\{[^/]+}", "[^/]+") + "$";
        entries.add(new Entry(new ApiTesterEndpointDescriptor(service, method, pathTemplate, description, destructive), Pattern.compile(regex)));
    }

    public List<ApiTesterEndpointDescriptor> all() {
        return entries.stream().map(Entry::descriptor).toList();
    }

    /** The one real security check - a request is only ever proxied if it exactly matches a real, allowlisted (service, method, path template) triple. */
    public Optional<ApiTesterEndpointDescriptor> match(String service, String method, String path) {
        if (service == null || method == null || path == null) return Optional.empty();
        return entries.stream()
                .filter(e -> e.descriptor().service().equals(service)
                        && e.descriptor().method().equalsIgnoreCase(method)
                        && e.pathPattern().matcher(path).matches())
                .findFirst()
                .map(Entry::descriptor);
    }
}
