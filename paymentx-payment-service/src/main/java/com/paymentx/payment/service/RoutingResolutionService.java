package com.paymentx.payment.service;

import com.paymentx.payment.client.RoutingClient;
import com.paymentx.payment.client.RoutingResolutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Orchestrates the real Routing Service resolution contract already
 * implemented and tested in Routing Service itself:
 *   participant-specific active route  → use it
 *   else the payment type's default route → use it
 *   else no route exists at all → ROUTE_NOT_FOUND (non-retryable)
 *
 * This mirrors RoutingServiceImpl.resolveRoute()'s own existing fallback
 * order exactly - this class does not invent new routing logic, it
 * performs the same two-step lookup from the caller's side that Routing
 * Service already performs internally when its own REST API is called
 * directly instead.
 *
 * A ROUTING_SERVICE_UNAVAILABLE/ROUTING_TIMEOUT failure here is NOT
 * converted into "try the old internal gateway selection instead" - the
 * caller (DebitProcessor/CreditProcessor) is responsible for treating any
 * exception from this class as processing failure, never as a signal to
 * bypass Routing Service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RoutingResolutionService {

    private final RoutingClient routingClient;

    public String resolveTargetRoute(String paymentType, String participantId, String correlationId) {
        log.info("Routing requested paymentType={} participantId={} correlationId={}",
                paymentType, participantId, correlationId);

        Optional<String> participantRoute = routingClient.resolveForParticipant(participantId, paymentType, correlationId);
        if (participantRoute.isPresent()) {
            log.info("Routing resolved via participant-specific rule paymentType={} participantId={} targetRoute={}",
                    paymentType, participantId, participantRoute.get());
            return participantRoute.get();
        }

        Optional<String> defaultRoute = routingClient.resolveDefault(paymentType, correlationId);
        if (defaultRoute.isPresent()) {
            log.info("Routing resolved via type-default rule paymentType={} participantId={} targetRoute={}",
                    paymentType, participantId, defaultRoute.get());
            return defaultRoute.get();
        }

        log.warn("Routing failed - no route configured paymentType={} participantId={}", paymentType, participantId);
        throw new RoutingResolutionException(RoutingResolutionException.Reason.ROUTE_NOT_FOUND, false,
                "No active routing rule and no default configured for paymentType=" + paymentType);
    }
}
