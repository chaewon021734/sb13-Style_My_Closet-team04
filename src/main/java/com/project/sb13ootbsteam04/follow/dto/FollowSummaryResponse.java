package com.project.sb13ootbsteam04.follow.dto;

import java.util.UUID;

public record FollowSummaryResponse(
        UUID followeeId,
        long followerCount,
        long followingCount,
        boolean followedByMe,
        UUID followedByMeId,
        boolean followingMe
) {
}