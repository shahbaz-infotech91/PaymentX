package com.paymentx.payment.client;

/**
 * Local, minimal copy of the one field this service actually needs from
 * Routing Service's real RouteRuleResponse wire shape - deliberately NOT
 * a shared/imported DTO, matching this platform's established convention
 * of each service owning its own copy of a cross-service contract rather
 * than compile-coupling to another service's module (see e.g. every
 * service's own local PaymentScheme/RoutingScheme/Scheme enum instead of
 * a shared one). Only targetRoute is mapped; every other field on the
 * real response (id, scheme, participantId, priority, active, isDefault,
 * description, timestamps) is intentionally ignored.
 */
public record RouteResolutionResponse(String targetRoute) {
}
