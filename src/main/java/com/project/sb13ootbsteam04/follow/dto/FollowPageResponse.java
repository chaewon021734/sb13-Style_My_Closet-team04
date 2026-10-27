package com.project.sb13ootbsteam04.follow.dto;

import java.util.List;
import java.util.UUID;

public record FollowPageResponse(
        List<FollowResponse> data,
        String nextCursor,
        UUID nextIdAfter,
        boolean hasNext,
        long totalCount,
        String sortBy,
        String sortDirection
) {
}