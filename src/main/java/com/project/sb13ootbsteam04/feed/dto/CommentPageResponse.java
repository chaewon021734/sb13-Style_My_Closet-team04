package com.project.sb13ootbsteam04.feed.dto;

import java.util.List;
import java.util.UUID;

public record CommentPageResponse(
        List<CommentResponse> data,
        String nextCursor,
        UUID nextIdAfter,
        boolean hasNext,
        long totalCount,
        String sortBy,
        String sortDirection
) {
}