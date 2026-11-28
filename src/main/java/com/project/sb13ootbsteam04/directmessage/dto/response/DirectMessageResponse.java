package com.project.sb13ootbsteam04.directmessage.dto.response;

import com.project.sb13ootbsteam04.directmessage.entity.DirectMessage;
import com.project.sb13ootbsteam04.user.entity.User;
import java.time.Instant;
import java.util.UUID;

public record DirectMessageResponse(
    UUID id,
    Instant createdAt,
    UserSummary sender,
    UserSummary receiver,
    String content
) {

  // 명세의 UserSummary (프론트는 sender.userId로 내 메시지인지 구분한다)
  public record UserSummary(
      UUID userId,
      String name,
      String profileImageUrl
  ) {

    public static UserSummary of(User user, String profileImageUrl) {
      return new UserSummary(user.getId(), user.getName(), profileImageUrl);
    }
  }

  public static DirectMessageResponse of(
      DirectMessage message,
      String senderProfileImageUrl,
      String receiverProfileImageUrl
  ) {
    return new DirectMessageResponse(
        message.getId(),
        message.getCreatedAt(),
        UserSummary.of(message.getSender(), senderProfileImageUrl),
        UserSummary.of(message.getReceiver(), receiverProfileImageUrl),
        message.getContent()
    );
  }
}
