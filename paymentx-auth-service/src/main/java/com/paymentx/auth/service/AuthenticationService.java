package com.paymentx.auth.service;

import com.paymentx.auth.dto.LoginRequest;
import com.paymentx.auth.dto.LoginResponse;

public interface AuthenticationService {

    /**
     * Throws com.paymentx.common.exception.UnauthorizedException (a
     * single, generic message) for every failure case - unknown
     * username, wrong password, and disabled account are deliberately
     * indistinguishable to the caller, per
     * PAYMENTX_AUTH_IMPLEMENTATION_DESIGN.md §14 (never leak whether a
     * username exists).
     */
    LoginResponse authenticate(LoginRequest request);
}
