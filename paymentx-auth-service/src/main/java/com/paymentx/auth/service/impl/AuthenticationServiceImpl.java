package com.paymentx.auth.service.impl;

import com.paymentx.auth.dto.LoginRequest;
import com.paymentx.auth.dto.LoginResponse;
import com.paymentx.auth.entity.AuthUser;
import com.paymentx.auth.repository.AuthUserRepository;
import com.paymentx.auth.repository.AuthUserRoleRepository;
import com.paymentx.auth.security.JwtIssuer;
import com.paymentx.auth.service.AuthenticationService;
import com.paymentx.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * authenticate() flow: find user -> check enabled -> verify BCrypt
 * password -> load roles -> resolve participantId -> generate JWT.
 * Every failure branch (unknown username, wrong password, disabled
 * account) throws the identical UnauthorizedException message - see this
 * interface's own javadoc for why.
 */
@Service
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final String GENERIC_FAILURE_MESSAGE = "Invalid username or password";

    private final AuthUserRepository authUserRepository;
    private final AuthUserRoleRepository authUserRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public AuthenticationServiceImpl(AuthUserRepository authUserRepository,
                                      AuthUserRoleRepository authUserRoleRepository,
                                      PasswordEncoder passwordEncoder,
                                      JwtIssuer jwtIssuer) {
        this.authUserRepository = authUserRepository;
        this.authUserRoleRepository = authUserRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    @Transactional
    public LoginResponse authenticate(LoginRequest request) {
        Optional<AuthUser> found = authUserRepository.findByUsername(request.username());

        if (found.isEmpty()) {
            log.warn("Authentication failed reason=unknown_username");
            throw new UnauthorizedException(GENERIC_FAILURE_MESSAGE);
        }

        AuthUser user = found.get();

        if (!user.isEnabled()) {
            log.warn("Authentication failed reason=disabled_account username={}", user.getUsername());
            throw new UnauthorizedException(GENERIC_FAILURE_MESSAGE);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            authUserRepository.save(user);
            log.warn("Authentication failed reason=invalid_credentials username={}", user.getUsername());
            throw new UnauthorizedException(GENERIC_FAILURE_MESSAGE);
        }

        if (user.getParticipantId() == null || user.getParticipantId().isBlank()) {
            // Fail safe rather than issue a token with a fabricated participant
            // ID - PAYMENTX_AUTH_IMPLEMENTATION_DESIGN.md §13.
            log.warn("Authentication failed reason=no_participant_association username={}", user.getUsername());
            throw new UnauthorizedException(GENERIC_FAILURE_MESSAGE);
        }

        user.setFailedLoginCount(0);
        authUserRepository.save(user);

        List<String> roles = authUserRoleRepository.findByAuthUser(user).stream()
                .map(com.paymentx.auth.entity.AuthUserRole::getRole)
                .toList();

        JwtIssuer.IssuedToken issued = jwtIssuer.issue(user.getUsername(), user.getParticipantId(), roles);

        log.info("Authentication succeeded username={} participantId={} roleCount={}",
                user.getUsername(), user.getParticipantId(), roles.size());

        return new LoginResponse(issued.token(), "Bearer", issued.expiresInSeconds());
    }
}
