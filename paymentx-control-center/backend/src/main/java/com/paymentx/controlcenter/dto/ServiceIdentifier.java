package com.paymentx.controlcenter.dto;

import java.util.function.Function;
import com.paymentx.controlcenter.config.ControlCenterProperties;

/**
 * ENGLISH: The fixed, allowlisted set of the 9 real PaymentX business
 * services this backend is allowed to call. What it does: pairs each
 * service's stable identifier/display name with a function that reads
 * its real base URL from ControlCenterProperties.Services. Why it
 * exists: this enum IS the SSRF allowlist for service-health calls -
 * every controller endpoint takes a ServiceIdentifier (an enum value
 * bound from a path segment), never a raw URL string, so there is no
 * code path where a browser-supplied string becomes an outbound
 * request target. How it will communicate with the backend: consumed
 * by ServiceHealthClient to resolve which real URL to call.
 *
 * HINGLISH: 9 real PaymentX business services ka fixed, allowlisted
 * set jinhe ye backend call karne ki ijazat hai. Ye kya karti hai: har
 * service ke stable identifier/display name ko ek function ke saath
 * pair karta hai jo ControlCenterProperties.Services se uska real base
 * URL padhta hai. Ye dashboard me kyu hai: ye enum service-health
 * calls ke liye SSRF allowlist HAI - har controller endpoint ek
 * ServiceIdentifier leta hai (ek enum value jo path segment se bind
 * hoti hai), kabhi raw URL string nahi, isliye koi code path nahi hai
 * jahan browser-supplied string ek outbound request target ban jaye.
 * Backend se kaise connect hogi: ServiceHealthClient ise consume karta
 * hai ye resolve karne ke liye ki konsa real URL call karna hai.
 */
public enum ServiceIdentifier {
    API_GATEWAY("api-gateway", "API Gateway", ControlCenterProperties.Services::getApiGatewayUrl),
    AUTH_SERVICE("auth-service", "Auth Service", ControlCenterProperties.Services::getAuthServiceUrl),
    VALIDATION_SERVICE("validation-service", "Validation Service", ControlCenterProperties.Services::getValidationServiceUrl),
    PAYMENT_SERVICE("payment-service", "Payment Service", ControlCenterProperties.Services::getPaymentServiceUrl),
    ROUTING_SERVICE("routing-service", "Routing Service", ControlCenterProperties.Services::getRoutingServiceUrl),
    AUDIT_SERVICE("audit-service", "Audit Service", ControlCenterProperties.Services::getAuditServiceUrl),
    NOTIFICATION_SERVICE("notification-service", "Notification Service", ControlCenterProperties.Services::getNotificationServiceUrl),
    RECONCILIATION_SERVICE("reconciliation-service", "Reconciliation Service", ControlCenterProperties.Services::getReconciliationServiceUrl),
    REPORTING_SERVICE("reporting-service", "Reporting Service", ControlCenterProperties.Services::getReportingServiceUrl);

    private final String slug;
    private final String displayName;
    private final Function<ControlCenterProperties.Services, String> urlResolver;

    ServiceIdentifier(String slug, String displayName, Function<ControlCenterProperties.Services, String> urlResolver) {
        this.slug = slug;
        this.displayName = displayName;
        this.urlResolver = urlResolver;
    }

    public String slug() {
        return slug;
    }

    public String displayName() {
        return displayName;
    }

    public String resolveBaseUrl(ControlCenterProperties.Services services) {
        return urlResolver.apply(services);
    }

    public static ServiceIdentifier fromSlug(String slug) {
        for (ServiceIdentifier value : values()) {
            if (value.slug.equals(slug)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown service identifier: " + slug);
    }
}
