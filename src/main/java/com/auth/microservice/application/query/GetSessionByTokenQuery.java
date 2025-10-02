package com.auth.microservice.application.query;

import com.auth.microservice.common.cqrs.Query;

import java.util.Objects;

/**
 * Query to get a session by its token (access or refresh token).
 * Used for token validation and session management operations.
 */
public class GetSessionByTokenQuery extends Query {
    private final String token;
    private final TokenType tokenType;
    private final boolean validateExpiration;

    public enum TokenType {
        ACCESS_TOKEN,
        REFRESH_TOKEN,
        ANY // Will search both access and refresh tokens
    }

    public GetSessionByTokenQuery(String userId, String token, TokenType tokenType, boolean validateExpiration) {
        super(userId);
        this.token = Objects.requireNonNull(token, "Token cannot be null");
        this.tokenType = Objects.requireNonNull(tokenType, "Token type cannot be null");
        this.validateExpiration = validateExpiration;
    }

    public GetSessionByTokenQuery(String userId, String token, TokenType tokenType) {
        this(userId, token, tokenType, true);
    }

    public GetSessionByTokenQuery(String userId, String token) {
        this(userId, token, TokenType.ANY, true);
    }

    public String getToken() {
        return token;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public boolean isValidateExpiration() {
        return validateExpiration;
    }

    @Override
    public String toString() {
        return String.format("GetSessionByTokenQuery{tokenType=%s, validateExpiration=%s, %s}", 
            tokenType, validateExpiration, super.toString());
    }
}