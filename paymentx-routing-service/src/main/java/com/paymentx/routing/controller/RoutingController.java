package com.paymentx.routing.controller;

import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.PageResponse;
import com.paymentx.routing.dto.RouteRuleRequest;
import com.paymentx.routing.dto.RouteRuleResponse;
import com.paymentx.routing.dto.RouteSearchCriteria;
import com.paymentx.routing.entity.RoutingScheme;
import com.paymentx.routing.service.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * WHY @PreAuthorize on mutating endpoints even though SecurityConfig
 * permits all requests through: the trust boundary is "API Gateway
 * validates the JWT and forwards it" (see SecurityConfig javadoc,
 * matching Validation Service's established pattern) - authentication
 * happens at the Gateway, but AUTHORIZATION (does THIS caller have the
 * ROUTING_ADMIN role to mutate routing rules) is this service's own
 * decision, made from the JWT roles claim Gateway propagates. Read
 * endpoints (list/search/resolve) stay open to any authenticated
 * service-to-service caller; only create/update/delete require the
 * admin role.
 */
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Routing", description = "Routing rule management and route resolution")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingController is a REST controller in the routing module of PaymentX. It lives in package com.paymentx.routing.controller and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingController PaymentX ke routing module ka ek REST controller hai. Ye com.paymentx.routing.controller package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping
    @PreAuthorize("hasRole('ROUTING_ADMIN')")
    @Operation(summary = "Create a routing rule (admin only)")
    public ResponseEntity<ApiResponse<RouteRuleResponse>> create(@Valid @RequestBody RouteRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(routingService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROUTING_ADMIN')")
    @Operation(summary = "Update a routing rule (admin only)")
    public ResponseEntity<ApiResponse<RouteRuleResponse>> update(@PathVariable UUID id, @Valid @RequestBody RouteRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(routingService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROUTING_ADMIN')")
    @Operation(summary = "Delete a routing rule (admin only)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        routingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Search/list routing rules with pagination and optional filters")
    public ResponseEntity<ApiResponse<PageResponse<RouteRuleResponse>>> search(
            @RequestParam(required = false) RoutingScheme scheme,
            @RequestParam(required = false) String participantId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean isDefault,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        RouteSearchCriteria criteria = new RouteSearchCriteria(scheme, participantId, active, isDefault);
        return ResponseEntity.ok(ApiResponse.success(routingService.search(criteria, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a routing rule by id")
    public ResponseEntity<ApiResponse<RouteRuleResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(routingService.getById(id)));
    }

    @GetMapping("/default")
    @Operation(summary = "Get the default routing rule for a scheme")
    public ResponseEntity<ApiResponse<RouteRuleResponse>> getDefault(
            @RequestParam RoutingScheme scheme,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ResponseEntity.ok(ApiResponse.success(routingService.resolveRoute(scheme, null, traceId)));
    }

    @GetMapping("/participant/{participantId}")
    @Operation(summary = "Resolve the active route for a participant + scheme, falling back to the scheme default")
    public ResponseEntity<ApiResponse<RouteRuleResponse>> resolveForParticipant(
            @PathVariable String participantId,
            @RequestParam RoutingScheme scheme,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ResponseEntity.ok(ApiResponse.success(routingService.resolveRoute(scheme, participantId, traceId)));
    }
}
