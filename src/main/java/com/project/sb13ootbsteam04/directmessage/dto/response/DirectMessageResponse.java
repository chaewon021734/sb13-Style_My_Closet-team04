package com.project.sb13ootbsteam04.directmessage.dto.response;

import com.project.sb13ootbsteam04.directmessage.entity.DirectMessage;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;
import java.time.Instant;
import java.util.UUID;

public record DirectMessageResponse(
    UUID id,
    Instant createdAt,
    UserDto sender,
    UserDto receiver,
    String content
) {
  public static DirectMessageResponse from(DirectMessage message) {
    return new DirectMessageResponse(
        message.getId(),
        message.getCreatedAt(),
        UserDto.from(message.getSender()),
        UserDto.from(message.getReceiver()),
        message.getContent()
    );
  }
}