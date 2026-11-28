package com.project.sb13ootbsteam04.feed.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        Instant createdAt,
        UUID feedId,
        AuthorDto author,
        String content
) {

    public record AuthorDto(
            UUID userId,
            String name,
            String profileImageUrl
    ) {
    }
}