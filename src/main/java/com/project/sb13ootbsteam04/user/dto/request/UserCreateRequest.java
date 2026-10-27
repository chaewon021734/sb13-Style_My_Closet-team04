package com.project.sb13ootbsteam04.user.dto.request;

public record UserCreateRequest(
        String name,
        String email,
        String password
) {
}
