package com.project.sb13ootbsteam04.directmessage.service;

import com.project.sb13ootbsteam04.directmessage.repository.DirectMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

  private final DirectMessageRepository directMessageRepository;

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

    if (cursor != null) {
      try {
        Instant.parse(cursor);
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException(
            "cursor 날짜 형식이 올바르지 않습니다.", e
        );
      }
    }

    // 첫 페이지 메시지 조회
    if (cursor == null) {
      directMessageRepository.findFirstPage(
          userId,
          targetUserId,
          PageRequest.of(0, limit + 1)
      );
    }
  }
}