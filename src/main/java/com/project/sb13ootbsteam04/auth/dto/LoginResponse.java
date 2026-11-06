package com.project.sb13ootbsteam04.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType
) {
}
