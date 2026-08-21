package com.paymentx.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalizes Routing Service connection settings so they are tunable
 * without a code change/redeploy - same convention as RetryProperties/
 * SchedulerProperties in this package. Bound from payment.routing-client.*
 * in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "payment.routing-client")
public class RoutingClientProperties {

    /** Called on Routing Service's own direct port - no API Gateway route
     *  exists for it, matching the same direct-call convention already
     *  established by MCP Gateway's RoutingServiceClient and Control
     *  Center's ApiTesterAllowlist. */
    private String baseUrl = "http://localhost:8084";

    private int connectTimeoutMs = 2000;

    private int readTimeoutMs = 3000;

    /** Final intended state is true (Routing Service participates in every
     *  live payment) - false only for an emergency rollback without a
     *  redeploy, per the platform's existing boolean-flag convention (see
     *  Control Center's control-center.ai.enabled). */
    private boolean enabled = true;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
