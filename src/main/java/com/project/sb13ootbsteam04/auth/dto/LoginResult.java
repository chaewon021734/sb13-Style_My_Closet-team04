package com.project.sb13ootbsteam04.auth.dto;

import com.project.sb13ootbsteam04.auth.service.RefreshTokenService.IssuedRefreshToken;

public record LoginResult(
        LoginResponse response,
        IssuedRefreshToken refreshToken
) {

    public boolean hasRefreshToken() {
        return refreshToken != null;
    }

    @Override
    public String toString() {
        return "LoginResult[REDACTED]";
    }
}