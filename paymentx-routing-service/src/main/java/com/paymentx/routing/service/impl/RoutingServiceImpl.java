package com.paymentx.routing.service.impl;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.routing.cache.RouteCacheService;
import com.paymentx.routing.dto.RouteRuleRequest;
import com.paymentx.routing.dto.RouteRuleResponse;
import com.paymentx.routing.dto.RouteSearchCriteria;
import com.paymentx.routing.entity.RoutingRule;
import com.paymentx.routing.entity.RoutingScheme;
import com.paymentx.routing.event.RouteResolvedEvent;
import com.paymentx.routing.event.RoutingEventProducer;
import com.paymentx.routing.event.RuleChangeApplicationEvent;
import com.paymentx.routing.event.RuleChangedEvent;
import com.paymentx.routing.mapper.RouteRuleMapper;
import com.paymentx.routing.metrics.RoutingMetrics;
import com.paymentx.routing.repository.RoutingRuleRepository;
import com.paymentx.routing.repository.RoutingRuleSpecifications;
import com.paymentx.routing.service.RoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingServiceImpl is a service in the routing module of PaymentX. It lives in package com.paymentx.routing.service.impl and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingServiceImpl PaymentX ke routing module ka ek service hai. Ye com.paymentx.routing.service.impl package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RoutingServiceImpl implements RoutingService {

    private final RoutingRuleRepository routingRuleRepository;
    private final RouteRuleMapper routeRuleMapper;
    private final RouteCacheService routeCacheService;
    private final RoutingEventProducer routingEventProducer;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RoutingMetrics routingMetrics;

    @Override
    public RouteRuleResponse create(RouteRuleRequest request) {
        RoutingRule rule = routeRuleMapper.toEntity(request);
        if (rule.getActive() == null) {
            rule.setActive(true);
        }
        if (rule.getIsDefault() == null) {
            rule.setIsDefault(false);
        }

        // Duplicate prevention: same scheme+participant+priority is
        // ambiguous at resolution time (which one wins on a tie?) - reject
        // rather than silently letting the DB pick whichever row Postgres
        // happens to return first.
        if (routingRuleRepository.existsBySchemeAndParticipantIdAndPriority(rule.getScheme(), rule.getParticipantId(), rule.getPriority())) {
            throw new ConflictException("ROUTING_RULE_DUPLICATE",
                    "A rule already exists for scheme=" + rule.getScheme() + " participantId=" + rule.getParticipantId()
                            + " at priority=" + rule.getPriority() + " - choose a different priority");
        }

        RoutingRule saved = routingRuleRepository.save(rule);
        routeCacheService.evictAll();
        publishRuleChanged(saved, "CREATED", null);
        log.info("Routing rule created id={} scheme={} participantId={}", saved.getId(), saved.getScheme(), saved.getParticipantId());
        return routeRuleMapper.toResponse(saved);
    }

    @Override
    public RouteRuleResponse update(UUID id, RouteRuleRequest request) {
        RoutingRule rule = findOrThrow(id);

        boolean priorityOrKeyChanged = !rule.getScheme().equals(request.scheme())
                || !java.util.Objects.equals(rule.getParticipantId(), request.participantId())
                || !rule.getPriority().equals(request.priority());
        if (priorityOrKeyChanged && routingRuleRepository.existsBySchemeAndParticipantIdAndPriority(
                request.scheme(), request.participantId(), request.priority())) {
            throw new ConflictException("ROUTING_RULE_DUPLICATE",
                    "A rule already exists for scheme=" + request.scheme() + " participantId=" + request.participantId()
                            + " at priority=" + request.priority());
        }

        routeRuleMapper.updateEntityFromRequest(request, rule);
        RoutingRule saved = routingRuleRepository.save(rule);
        routeCacheService.evictAll();
        publishRuleChanged(saved, "UPDATED", null);
        log.info("Routing rule updated id={}", id);
        return routeRuleMapper.toResponse(saved);
    }

    @Override
    public void delete(UUID id) {
        RoutingRule rule = findOrThrow(id);
        routingRuleRepository.delete(rule);
        routeCacheService.evictAll();
        publishRuleChanged(rule, "DELETED", null);
        log.info("Routing rule deleted id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public RouteRuleResponse getById(UUID id) {
        return routeRuleMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RouteRuleResponse> search(RouteSearchCriteria criteria, int page, int size) {
        Specification<RoutingRule> spec = Specification
                .allOf(RoutingRuleSpecifications.hasScheme(criteria.scheme()))
                .and(RoutingRuleSpecifications.hasParticipantId(criteria.participantId()))
                .and(RoutingRuleSpecifications.isActive(criteria.active()))
                .and(RoutingRuleSpecifications.isDefault(criteria.isDefault()));

        var pageResult = routingRuleRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "priority")));

        return PageResponse.of(
                pageResult.getContent().stream().map(routeRuleMapper::toResponse).toList(),
                pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public RouteRuleResponse resolveRoute(RoutingScheme scheme, String participantId, String traceId) {
        var timerSample = routingMetrics.startRouteLookupTimer();

        var cached = routeCacheService.get(scheme.name(), participantId);
        if (cached.isPresent()) {
            routingMetrics.recordCacheHit(scheme.name());
            routingMetrics.recordRouteLookup(timerSample, scheme.name(), true);
            return cached.get();
        }
        routingMetrics.recordCacheMiss(scheme.name());

        RoutingRule resolved = routingRuleRepository
                .findFirstBySchemeAndParticipantIdAndActiveTrueOrderByPriorityAsc(scheme, participantId)
                .or(() -> routingRuleRepository.findFirstBySchemeAndIsDefaultTrueAndActiveTrueOrderByPriorityAsc(scheme))
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule",
                        "scheme=" + scheme + (participantId != null ? ",participantId=" + participantId : "") + " (no active rule, no default configured)"));

        RouteRuleResponse response = routeRuleMapper.toResponse(resolved);
        routeCacheService.put(scheme.name(), participantId, response);

        routingEventProducer.publishRouteResolved(RouteResolvedEvent.builder()
                .ruleId(resolved.getId())
                .scheme(scheme.name())
                .participantId(participantId)
                .targetRoute(resolved.getTargetRoute())
                .resolvedAt(OffsetDateTime.now())
                .build(), traceId);

        routingMetrics.recordRouteLookup(timerSample, scheme.name(), false);
        return response;
    }

    @Override
    public void deactivateRoutesForParticipant(String participantId, String traceId) {
        List<RoutingRule> activeRoutes = routingRuleRepository.findByParticipantIdAndActiveTrue(participantId);
        if (activeRoutes.isEmpty()) {
            log.info("No active routes to deactivate for participantId={}", participantId);
            return;
        }
        for (RoutingRule rule : activeRoutes) {
            rule.setActive(false);
            routingRuleRepository.save(rule);
            publishRuleChanged(rule, "UPDATED", traceId);
        }
        routeCacheService.evictAll();
        log.info("Deactivated {} route(s) for participantId={}", activeRoutes.size(), participantId);
    }

    private void publishRuleChanged(RoutingRule rule, String changeType, String traceId) {
        routingMetrics.recordRuleChange(changeType);
        routingMetrics.setActiveRouteCount(routingRuleRepository.countByActiveTrue());
        applicationEventPublisher.publishEvent(new RuleChangeApplicationEvent(this,
                RuleChangedEvent.builder()
                        .ruleId(rule.getId())
                        .scheme(rule.getScheme().name())
                        .participantId(rule.getParticipantId())
                        .changeType(changeType)
                        .changedAt(OffsetDateTime.now())
                        .build(),
                traceId));
    }

    private RoutingRule findOrThrow(UUID id) {
        return routingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", id.toString()));
    }
}
