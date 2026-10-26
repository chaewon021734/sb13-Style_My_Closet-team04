package com.project.sb13ootbsteam04.feed.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateFeedRequest(

        @NotBlank(message = "피드 내용은 필수입니다.")
        String content

) {
}