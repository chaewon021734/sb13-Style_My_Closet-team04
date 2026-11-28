package com.project.sb13ootbsteam04.directmessage.websocket;

import com.project.sb13ootbsteam04.directmessage.dto.response.DirectMessageResponse;

import java.util.UUID;

// 서버 간 DM 전달 채널과 대화 채널 이름 규칙
// 대화 채널: /sub/direct-messages_{작은 ID}_{큰 ID} (프론트와 같은 규칙)
public final class DirectMessageRedisChannel {

  public static final String NAME = "dm:messages";
  public static final String DESTINATION_PREFIX = "/sub/direct-messages_";

  private DirectMessageRedisChannel() {
  }

  public record Message(String dmKey, DirectMessageResponse message) {
  }

  public static String dmKey(UUID first, UUID second) {
    String a = first.toString();
    String b = second.toString();
    return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
  }

  public static String destination(String dmKey) {
    return DESTINATION_PREFIX + dmKey;
  }

  // 구독 경로가 대화 채널이고 사용자가 그 대화의 참여자인지
  public static boolean isParticipant(String destination, String userId) {
    if (destination == null || !destination.startsWith(DESTINATION_PREFIX)) {
      return false;
    }
    String[] ids = destination.substring(DESTINATION_PREFIX.length()).split("_");
    if (ids.length != 2) {
      return false;
    }
    try {
      UUID first = UUID.fromString(ids[0]);
      UUID second = UUID.fromString(ids[1]);
      return destination.equals(destination(dmKey(first, second)))
          && (first.toString().equals(userId) || second.toString().equals(userId));
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
