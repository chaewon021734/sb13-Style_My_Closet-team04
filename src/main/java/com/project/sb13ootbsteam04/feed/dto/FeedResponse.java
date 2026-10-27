package com.project.sb13ootbsteam04.feed.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedResponse(
        UUID id,
        Instant createdAt,
        Instant updatedAt,
        AuthorDto author,
        WeatherDto weather,
        List<OotdDto> ootds,
        String content,
        long likeCount,
        int commentCount,
        boolean likedByMe
) {

    public record AuthorDto(
            UUID userId,
            String name,
            String profileImageUrl
    ) {
    }

    public record WeatherDto(
            UUID weatherId,
            String skyStatus,
            PrecipitationDto precipitation,
            TemperatureDto temperature
    ) {
    }

    public record PrecipitationDto(
            String type,
            Double amount,
            Double probability
    ) {
    }

    public record TemperatureDto(
            Double current,
            Double comparedToDayBefore,
            Double min,
            Double max
    ) {
    }

    public record OotdDto(
            UUID clothesId,
            String name,
            String imageUrl,
            String type,
            List<AttributeDto> attributes
    ) {
    }

    public record AttributeDto(
            UUID definitionId,
            String definitionName,
            List<String> selectableValues,
            String value
    ) {
    }
}