package com.project.sb13ootbsteam04.auth.dto;

import com.project.sb13ootbsteam04.user.dto.response.UserDto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        UserDto userDto
) {
}