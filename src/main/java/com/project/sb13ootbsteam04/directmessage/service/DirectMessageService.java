package com.project.sb13ootbsteam04.directmessage.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DirectMessageService {

  public void getMessages(
      UUID userId,
      UUID targetUserId,
      String cursor,
      UUID idAfter,
      int limit
  ) {
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit은 1 이상 100 이하여야 합니다.");
    }
    if ((cursor == null) != (idAfter == null)) {
      throw new IllegalArgumentException(
          "cursor와 idAfter는 함께 전달되어야 합니다."
      );
    }
  }
}