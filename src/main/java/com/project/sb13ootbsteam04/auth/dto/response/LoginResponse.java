package com.project.sb13ootbsteam04.auth.dto.response;

public record LoginResponse(
        String accessToken,
        String tokenType
) {
}
