package com.project.sb13ootbsteam04.follow.dto;

import java.util.UUID;

public record FollowResponse(
        UUID id,
        UserDto followee,
        UserDto follower
) {

    public record UserDto(
            UUID userId,
            String name,
            String profileImageUrl
    ) {
    }
}