package com.project.sb13ootbsteam04.directmessage.dto.request;

import java.util.UUID;

public record DirectMessageRequest(
    UUID receiverId,
    String content
) {
}