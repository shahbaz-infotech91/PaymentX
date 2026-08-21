package com.paymentx.auth.entity;

import com.paymentx.common.base.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Identity + credential in one table (deliberately not split into a
 * separate "credential" table - see PAYMENTX_AUTH_IMPLEMENTATION_DESIGN.md
 * §5: a separate credential table is only warranted if a user could have
 * multiple credential types, which nothing in this platform currently
 * requires).
 *
 * participantId is a plain string reference to Validation Service's own,
 * already-authoritative participant record (its real participant table,
 * seeded with values like "BANK001") - NOT a duplicate participant model.
 * Auth Service and Validation Service are separate databases; this
 * mirrors the same "service-local copy of a shared identifier" pattern
 * already used platform-wide (e.g. every service's own local PaymentType-
 * shaped enum instead of importing another service's).
 */
@Entity
@Table(name = "auth_user")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser extends AuditableEntity {

    @Column(name = "username", nullable = false, unique = true, length = 128)
    private String username;

    /** BCrypt output only - never a plaintext value, never returned in
     *  any API response DTO. */
    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(name = "participant_id", nullable = false, length = 64)
    private String participantId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private int failedLoginCount = 0;
}
