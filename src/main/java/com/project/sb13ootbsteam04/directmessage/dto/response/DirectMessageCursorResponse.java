package com.project.sb13ootbsteam04.directmessage.dto.response;

import java.util.List;
import java.util.UUID;

public record DirectMessageCursorResponse(
    List<DirectMessageResponse> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount
) {
}