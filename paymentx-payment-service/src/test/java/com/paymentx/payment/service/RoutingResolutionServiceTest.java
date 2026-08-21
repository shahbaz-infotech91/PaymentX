package com.paymentx.payment.service;

import com.paymentx.payment.client.RoutingClient;
import com.paymentx.payment.client.RoutingResolutionException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers RoutingResolutionService's fallback order - the same
 * participant-rule -> type-default -> no-route order Routing Service's
 * own RoutingServiceImpl.resolveRoute() already implements and tests
 * internally; this class only re-implements that order from the caller's
 * side (a real REST call per step), so these tests verify the ORDER and
 * SHORT-CIRCUITING of those two calls, not routing business logic itself
 * (which stays entirely owned by Routing Service, unchanged).
 */
class RoutingResolutionServiceTest {

    private final RoutingClient routingClient = mock(RoutingClient.class);
    private final RoutingResolutionService service = new RoutingResolutionService(routingClient);

    @Test
    void participantSpecificRoute_isUsed_defaultNeverCalled() {
        when(routingClient.resolveForParticipant("BANK001", "INSTANT_PAYMENT", "corr-1"))
                .thenReturn(Optional.of("instant-payment-processor-bank001"));

        String result = service.resolveTargetRoute("INSTANT_PAYMENT", "BANK001", "corr-1");

        assertThat(result).isEqualTo("instant-payment-processor-bank001");
        verify(routingClient).resolveForParticipant("BANK001", "INSTANT_PAYMENT", "corr-1");
        verifyNoMoreInteractions(routingClient);
    }

    @Test
    void noParticipantRoute_fallsBackToTypeDefault() {
        when(routingClient.resolveForParticipant("BANK999", "CARD_PAYMENT", "corr-2"))
                .thenReturn(Optional.empty());
        when(routingClient.resolveDefault("CARD_PAYMENT", "corr-2"))
                .thenReturn(Optional.of("card-payment-processor"));

        String result = service.resolveTargetRoute("CARD_PAYMENT", "BANK999", "corr-2");

        assertThat(result).isEqualTo("card-payment-processor");
        verify(routingClient).resolveForParticipant("BANK999", "CARD_PAYMENT", "corr-2");
        verify(routingClient).resolveDefault("CARD_PAYMENT", "corr-2");
    }

    @Test
    void noParticipantRoute_noDefault_throwsRouteNotFound() {
        when(routingClient.resolveForParticipant("BANK999", "REAL_TIME_PAYMENT", "corr-3"))
                .thenReturn(Optional.empty());
        when(routingClient.resolveDefault("REAL_TIME_PAYMENT", "corr-3"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveTargetRoute("REAL_TIME_PAYMENT", "BANK999", "corr-3"))
                .isInstanceOf(RoutingResolutionException.class)
                .satisfies(ex -> {
                    RoutingResolutionException routingEx = (RoutingResolutionException) ex;
                    assertThat(routingEx.getReason()).isEqualTo(RoutingResolutionException.Reason.ROUTE_NOT_FOUND);
                    assertThat(routingEx.isRetryable()).isFalse();
                });
    }

    @Test
    void routingServiceUnavailable_propagatesAsRetryable_doesNotFallBackToDefault() {
        when(routingClient.resolveForParticipant("BANK001", "INSTANT_PAYMENT", "corr-4"))
                .thenThrow(new RoutingResolutionException(
                        RoutingResolutionException.Reason.ROUTING_SERVICE_UNAVAILABLE, true, "simulated outage"));

        assertThatThrownBy(() -> service.resolveTargetRoute("INSTANT_PAYMENT", "BANK001", "corr-4"))
                .isInstanceOf(RoutingResolutionException.class)
                .satisfies(ex -> {
                    RoutingResolutionException routingEx = (RoutingResolutionException) ex;
                    assertThat(routingEx.getReason()).isEqualTo(RoutingResolutionException.Reason.ROUTING_SERVICE_UNAVAILABLE);
                    assertThat(routingEx.isRetryable()).isTrue();
                });
        // The exception from the FIRST call propagates directly - resolveDefault() must
        // never be called as a workaround for an unavailable routing service.
        verify(routingClient).resolveForParticipant("BANK001", "INSTANT_PAYMENT", "corr-4");
        verifyNoMoreInteractions(routingClient);
    }
}
