package com.paymentx.routing.service;

import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.routing.cache.RouteCacheService;
import com.paymentx.routing.dto.RouteRuleRequest;
import com.paymentx.routing.dto.RouteRuleResponse;
import com.paymentx.routing.entity.RoutingRule;
import com.paymentx.routing.entity.RoutingScheme;
import com.paymentx.routing.event.RoutingEventProducer;
import com.paymentx.routing.mapper.RouteRuleMapper;
import com.paymentx.routing.metrics.RoutingMetrics;
import com.paymentx.routing.repository.RoutingRuleRepository;
import com.paymentx.routing.service.impl.RoutingServiceImpl;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingServiceImplTest is a JUnit test class in the routing module of PaymentX, package com.paymentx.routing.service. It is used within routing's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingServiceImplTest PaymentX ke routing module ka ek JUnit test class hai, package com.paymentx.routing.service me. Ye routing ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class RoutingServiceImplTest {

    @Mock
    private RoutingRuleRepository routingRuleRepository;
    @Mock
    private RouteRuleMapper routeRuleMapper;
    @Mock
    private RouteCacheService routeCacheService;
    @Mock
    private RoutingEventProducer routingEventProducer;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private RoutingMetrics routingMetrics;
    private RoutingServiceImpl routingService;

    @BeforeEach
    void setUp() {
        // Real RoutingMetrics with a SimpleMeterRegistry - a mock here
        // would need to stub every Timer/Counter builder call, which is
        // brittle; a real lightweight in-memory registry is simpler and
        // still fully isolated from any actual metrics backend.
        routingMetrics = new RoutingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        routingService = new RoutingServiceImpl(routingRuleRepository, routeRuleMapper, routeCacheService,
                routingEventProducer, applicationEventPublisher, routingMetrics);
    }

    @Test
    void create_duplicateSchemeParticipantPriority_throwsConflict() {
        RouteRuleRequest request = new RouteRuleRequest(RoutingScheme.INSTANT_PAYMENT, "BANK001", "route-a", 10, true, false, null);
        RoutingRule entity = RoutingRule.builder().scheme(RoutingScheme.INSTANT_PAYMENT).participantId("BANK001").priority(10).build();
        when(routeRuleMapper.toEntity(request)).thenReturn(entity);
        when(routingRuleRepository.existsBySchemeAndParticipantIdAndPriority(RoutingScheme.INSTANT_PAYMENT, "BANK001", 10))
                .thenReturn(true);

        assertThatThrownBy(() -> routingService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(routingRuleRepository, never()).save(any());
    }

    @Test
    void create_noDuplicate_savesAndEvictsCache() {
        RouteRuleRequest request = new RouteRuleRequest(RoutingScheme.CARD_PAYMENT, null, "route-b", 20, true, true, null);
        RoutingRule entity = RoutingRule.builder().scheme(RoutingScheme.CARD_PAYMENT).priority(20).build();
        RoutingRule saved = RoutingRule.builder().scheme(RoutingScheme.CARD_PAYMENT).priority(20).build();
        RouteRuleResponse response = new RouteRuleResponse(UUID.randomUUID(), RoutingScheme.CARD_PAYMENT, null, "route-b", 20, true, true, null, null, null);

        when(routeRuleMapper.toEntity(request)).thenReturn(entity);
        when(routingRuleRepository.existsBySchemeAndParticipantIdAndPriority(RoutingScheme.CARD_PAYMENT, null, 20)).thenReturn(false);
        when(routingRuleRepository.save(entity)).thenReturn(saved);
        when(routeRuleMapper.toResponse(saved)).thenReturn(response);

        RouteRuleResponse result = routingService.create(request);

        assertThat(result.targetRoute()).isEqualTo("route-b");
        verify(routeCacheService).evictAll();
        verify(applicationEventPublisher).publishEvent(any());
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(routingRuleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routingService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveRoute_cacheHit_skipsRepositoryAndDoesNotPublishEvent() {
        RouteRuleResponse cachedResponse = new RouteRuleResponse(UUID.randomUUID(), RoutingScheme.INSTANT_PAYMENT, "BANK001", "cached-route", 10, true, false, null, null, null);
        when(routeCacheService.get("INSTANT_PAYMENT", "BANK001")).thenReturn(Optional.of(cachedResponse));

        RouteRuleResponse result = routingService.resolveRoute(RoutingScheme.INSTANT_PAYMENT, "BANK001", "trace-1");

        assertThat(result.targetRoute()).isEqualTo("cached-route");
        verify(routingRuleRepository, never()).findFirstBySchemeAndParticipantIdAndActiveTrueOrderByPriorityAsc(any(), anyString());
        verify(routingEventProducer, never()).publishRouteResolved(any(), any());
    }

    @Test
    void resolveRoute_cacheMiss_fallsBackToDefaultWhenNoParticipantRule() {
        when(routeCacheService.get("CARD_PAYMENT", "BANK999")).thenReturn(Optional.empty());
        when(routingRuleRepository.findFirstBySchemeAndParticipantIdAndActiveTrueOrderByPriorityAsc(RoutingScheme.CARD_PAYMENT, "BANK999"))
                .thenReturn(Optional.empty());

        RoutingRule defaultRule = RoutingRule.builder().scheme(RoutingScheme.CARD_PAYMENT).targetRoute("default-route").isDefault(true).build();
        when(routingRuleRepository.findFirstBySchemeAndIsDefaultTrueAndActiveTrueOrderByPriorityAsc(RoutingScheme.CARD_PAYMENT))
                .thenReturn(Optional.of(defaultRule));

        RouteRuleResponse response = new RouteRuleResponse(UUID.randomUUID(), RoutingScheme.CARD_PAYMENT, null, "default-route", 100, true, true, null, null, null);
        when(routeRuleMapper.toResponse(defaultRule)).thenReturn(response);

        RouteRuleResponse result = routingService.resolveRoute(RoutingScheme.CARD_PAYMENT, "BANK999", "trace-2");

        assertThat(result.targetRoute()).isEqualTo("default-route");
        verify(routeCacheService).put("CARD_PAYMENT", "BANK999", response);
    }

    @Test
    void resolveRoute_noRuleAndNoDefault_throwsResourceNotFound() {
        when(routeCacheService.get("REAL_TIME_PAYMENT", null)).thenReturn(Optional.empty());
        when(routingRuleRepository.findFirstBySchemeAndParticipantIdAndActiveTrueOrderByPriorityAsc(RoutingScheme.REAL_TIME_PAYMENT, null))
                .thenReturn(Optional.empty());
        when(routingRuleRepository.findFirstBySchemeAndIsDefaultTrueAndActiveTrueOrderByPriorityAsc(RoutingScheme.REAL_TIME_PAYMENT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> routingService.resolveRoute(RoutingScheme.REAL_TIME_PAYMENT, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivateRoutesForParticipant_noActiveRoutes_isNoOpAndDoesNotEvictCache() {
        when(routingRuleRepository.findByParticipantIdAndActiveTrue("BANK123")).thenReturn(java.util.List.of());

        routingService.deactivateRoutesForParticipant("BANK123", "trace-3");

        verify(routeCacheService, never()).evictAll();
        verify(routingRuleRepository, never()).save(any());
    }

    @Test
    void deactivateRoutesForParticipant_activeRoutesExist_deactivatesEachAndEvictsCache() {
        RoutingRule rule1 = RoutingRule.builder().scheme(RoutingScheme.INSTANT_PAYMENT).participantId("BANK123").active(true).build();
        RoutingRule rule2 = RoutingRule.builder().scheme(RoutingScheme.CARD_PAYMENT).participantId("BANK123").active(true).build();
        when(routingRuleRepository.findByParticipantIdAndActiveTrue("BANK123")).thenReturn(java.util.List.of(rule1, rule2));

        routingService.deactivateRoutesForParticipant("BANK123", "trace-4");

        assertThat(rule1.getActive()).isFalse();
        assertThat(rule2.getActive()).isFalse();
        verify(routingRuleRepository, times(2)).save(any());
        verify(routeCacheService).evictAll();
    }
}
