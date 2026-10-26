package com.project.sb13ootbsteam04.feed.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record CreateFeedRequest(

        @NotNull
        UUID authorId,

        @NotNull
        UUID weatherId,

        @NotNull
        List<UUID> clothesIds,

        @NotBlank
        String content

) {
}