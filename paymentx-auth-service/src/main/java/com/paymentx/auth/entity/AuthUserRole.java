package com.paymentx.auth.entity;

import com.paymentx.common.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A plain (user, role) pair - not a full role/permission model. No
 * downstream service in this platform currently checks anything more
 * granular than a role-name string (@PreAuthorize("hasRole('...')")), so
 * a normalized Role/Permission entity hierarchy would be speculative, not
 * evidence-grounded (see PAYMENTX_AUTH_IMPLEMENTATION_DESIGN.md §5).
 */
@Entity
@Table(name = "auth_user_role", uniqueConstraints = @UniqueConstraint(name = "uq_auth_user_role", columnNames = {"user_id", "role"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserRole extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_auth_user_role_user"))
    private AuthUser authUser;

    /** Raw role name, e.g. "ROUTING_ADMIN" - no "ROLE_" prefix stored
     *  here. API Gateway's existing JwtGrantedAuthoritiesConverter adds
     *  that prefix when converting the JWT's roles claim into a Spring
     *  Security authority - storing it here too would double it. */
    @Column(name = "role", nullable = false, length = 64)
    private String role;
}
