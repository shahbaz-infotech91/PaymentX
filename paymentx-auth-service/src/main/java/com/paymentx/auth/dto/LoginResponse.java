package com.paymentx.auth.dto;

/**
 * "tokenType" here is the HTTP response field describing how to use the
 * token ("Bearer") - not to be confused with the JWT-internal
 * SecurityConstants.CLAIM_TOKEN_TYPE claim (ACCESS/REFRESH/SERVICE),
 * which is a different, unrelated concept issued inside accessToken
 * itself.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}
