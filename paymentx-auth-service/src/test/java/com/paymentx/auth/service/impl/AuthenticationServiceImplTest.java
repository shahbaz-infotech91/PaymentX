package com.paymentx.auth.service.impl;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.paymentx.auth.config.AuthJwtProperties;
import com.paymentx.auth.dto.LoginRequest;
import com.paymentx.auth.dto.LoginResponse;
import com.paymentx.auth.entity.AuthUser;
import com.paymentx.auth.entity.AuthUserRole;
import com.paymentx.auth.repository.AuthUserRepository;
import com.paymentx.auth.repository.AuthUserRoleRepository;
import com.paymentx.auth.security.JwtIssuer;
import com.paymentx.common.constant.SecurityConstants;
import com.paymentx.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers scenarios 1-9 of this task's minimum test list: BCrypt
 * verification, valid authentication, invalid password, unknown user,
 * disabled user, real JWT generation, required-claims presence, correct
 * participantId, correct roles. Decodes the ACTUAL issued JWT with raw
 * Nimbus (SignedJWT.parse + MACVerifier, the same underlying primitives
 * NimbusReactiveJwtDecoder uses internally) against the EXACT signing
 * secret this test configures - proving real HMAC-SHA256 signature and
 * claim-shape correctness without needing API Gateway's own
 * spring-security-oauth2-resource-server dependency in this module.
 */
class AuthenticationServiceImplTest {

    private static final String TEST_SECRET = "test-only-signing-secret-not-a-real-secret-32c";

    private AuthUserRepository authUserRepository;
    private AuthUserRoleRepository authUserRoleRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        authUserRepository = mock(AuthUserRepository.class);
        authUserRoleRepository = mock(AuthUserRoleRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();

        AuthJwtProperties jwtProperties = new AuthJwtProperties();
        jwtProperties.setSecret(TEST_SECRET);
        jwtProperties.setIssuer("paymentx-auth-service-test");
        jwtProperties.setAccessTokenTtlSeconds(1800);
        JwtIssuer jwtIssuer = new JwtIssuer(jwtProperties);

        service = new AuthenticationServiceImpl(authUserRepository, authUserRoleRepository, passwordEncoder, jwtIssuer);
    }

    private AuthUser enabledUser(String rawPassword, String participantId) {
        return AuthUser.builder()
                .id(UUID.randomUUID())
                .username("test-user")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .participantId(participantId)
                .enabled(true)
                .failedLoginCount(0)
                .build();
    }

    @Test
    void bcryptPasswordVerification_matchesCorrectly() {
        // Scenario 1
        String hash = passwordEncoder.encode("Correct-Password-123");
        assertThat(passwordEncoder.matches("Correct-Password-123", hash)).isTrue();
        assertThat(passwordEncoder.matches("Wrong-Password", hash)).isFalse();
        assertThat(hash).isNotEqualTo("Correct-Password-123");
    }

    @Test
    void validAuthentication_returnsRealJwt_withCorrectClaims() throws Exception {
        // Scenarios 2, 6, 7, 8, 9
        AuthUser user = enabledUser("Correct-Password-123", "TEST-PARTICIPANT-001");
        when(authUserRepository.findByUsername("test-user")).thenReturn(Optional.of(user));
        when(authUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authUserRoleRepository.findByAuthUser(user)).thenReturn(List.of(
                AuthUserRole.builder().role("USER").build()));

        LoginResponse response = service.authenticate(new LoginRequest("test-user", "Correct-Password-123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(1800);

        // Decode the REAL issued token with the real signing secret - proves
        // it is a genuine, correctly-signed, correctly-shaped JWT, not just
        // a non-empty string.
        SignedJWT parsed = SignedJWT.parse(response.accessToken());
        assertThat(parsed.verify(new MACVerifier(TEST_SECRET.getBytes()))).isTrue();

        var claims = parsed.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo("test-user");
        assertThat(claims.getIssuer()).isEqualTo("paymentx-auth-service-test");
        assertThat(claims.getStringListClaim(SecurityConstants.CLAIM_ROLES)).containsExactly("USER");
        assertThat(claims.getStringClaim(SecurityConstants.CLAIM_PARTICIPANT_ID)).isEqualTo("TEST-PARTICIPANT-001");
        assertThat(claims.getStringClaim(SecurityConstants.CLAIM_TOKEN_TYPE)).isEqualTo(SecurityConstants.TOKEN_TYPE_ACCESS);
        assertThat(claims.getExpirationTime()).isAfter(claims.getIssueTime());
    }

    @Test
    void invalidPassword_throwsGenericUnauthorized() {
        // Scenario 3
        AuthUser user = enabledUser("Correct-Password-123", "TEST-PARTICIPANT-001");
        when(authUserRepository.findByUsername("test-user")).thenReturn(Optional.of(user));
        when(authUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("test-user", "Wrong-Password")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void unknownUser_throwsIdenticalGenericUnauthorized() {
        // Scenario 4 - message must be byte-for-byte identical to the wrong-password
        // case, so a client cannot distinguish "no such user" from "wrong password".
        when(authUserRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("nobody", "anything")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void disabledUser_throwsGenericUnauthorized_evenWithCorrectPassword() {
        // Scenario 5
        AuthUser user = AuthUser.builder()
                .id(UUID.randomUUID())
                .username("disabled-user")
                .passwordHash(passwordEncoder.encode("Correct-Password-123"))
                .participantId("TEST-PARTICIPANT-001")
                .enabled(false)
                .failedLoginCount(0)
                .build();
        when(authUserRepository.findByUsername("disabled-user")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("disabled-user", "Correct-Password-123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void missingParticipantAssociation_failsSafely_doesNotIssueTokenWithFabricatedParticipantId() {
        AuthUser user = enabledUser("Correct-Password-123", "");
        when(authUserRepository.findByUsername("test-user")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("test-user", "Correct-Password-123")))
                .isInstanceOf(UnauthorizedException.class);
    }
}
