package com.paymentx.payment.client;

/**
 * WHY a dedicated exception rather than reusing common-library's
 * RoutingException: RoutingException is a currently-unused shared type
 * with no established call site anywhere in the platform; introducing it
 * here for the first time would be a cross-cutting decision beyond this
 * integration's minimal scope. This local exception carries exactly what
 * the calling processor needs - a stable reason code and a retryable
 * flag - matching the same shape SchemeGateway.GatewayResult already
 * uses for the exact same purpose one call further down the pipeline.
 *
 * retryable=true means the failure is transient (Routing Service
 * unavailable/timed out) and the payment should be scheduled for a later
 * retry by the existing RetryScheduler - NOT that DebitProcessor/
 * CreditProcessor should fall back to the old internal gateway selection.
 * retryable=false means the failure is a genuine business outcome (no
 * route configured) and the payment should be marked permanently failed.
 */
public class RoutingResolutionException extends RuntimeException {

    public enum Reason {
        ROUTE_NOT_FOUND,
        ROUTING_SERVICE_UNAVAILABLE,
        ROUTING_TIMEOUT,
        INVALID_ROUTING_RESPONSE
    }

    private final Reason reason;
    private final boolean retryable;

    public RoutingResolutionException(Reason reason, boolean retryable, String message) {
        super(message);
        this.reason = reason;
        this.retryable = retryable;
    }

    public Reason getReason() {
        return reason;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
