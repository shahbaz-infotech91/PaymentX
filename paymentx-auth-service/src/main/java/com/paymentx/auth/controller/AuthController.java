package com.paymentx.auth.controller;

import com.paymentx.auth.dto.LoginRequest;
import com.paymentx.auth.dto.LoginResponse;
import com.paymentx.auth.service.AuthenticationService;
import com.paymentx.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one real business endpoint this module has. Must stay public (see
 * SecurityConfig's own updated javadoc) - a client cannot present a JWT
 * to obtain one. Never returns password/passwordHash/internal database
 * IDs - LoginResponse carries only accessToken/tokenType/expiresInSeconds.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
