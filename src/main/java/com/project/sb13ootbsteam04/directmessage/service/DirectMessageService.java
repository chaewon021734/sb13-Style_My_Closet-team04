
package com.project.sb13ootbsteam04.directmessage.service;

import com.project.sb13ootbsteam04.directmessage.dto.response.DirectMessageCursorResponse;
import com.project.sb13ootbsteam04.directmessage.dto.response.DirectMessageResponse;
import com.project.sb13ootbsteam04.directmessage.entity.DirectMessage;
import com.project.sb13ootbsteam04.directmessage.event.DirectMessageSentEvent;
import com.project.sb13ootbsteam04.directmessage.repository.DirectMessageRepository;
import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.exception.UserException;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectMessageService {

  private static final String SORT_BY = "createdAt";
  private static final String SORT_DIRECTION = "DESCENDING";

  private final DirectMessageRepository directMessageRepository;
  private final ProfileRepository profileRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  // DM 저장 후 커밋되면 두 사람에게 실시간 전달하고 받는 사람에게 알린다
  @Transactional
  public DirectMessageResponse send(UUID senderId, UUID receiverId, String content) {
    if (receiverId == null) {
      throw new IllegalArgumentException("받는 사람을 지정해야 합니다.");
    }
    User sender = userRepository.findById(senderId).orElseThrow(() -> UserException.userNotFound(senderId));
    User receiver = userRepository.findById(receiverId).orElseThrow(() -> UserException.userNotFound(receiverId));
    DirectMessage message = directMessageRepository.saveAndFlush(new DirectMessage(sender, receiver, content));

    Map<UUID, String> imageUrls = profileImageUrls(senderId, receiverId);
    DirectMessageResponse response = DirectMessageResponse.of(
        message, imageUrls.get(senderId), imageUrls.get(receiverId));
    eventPublisher.publishEvent(new DirectMessageSentEvent(response));
    return response;
  }

  public DirectMessageCursorResponse getMessages(
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

    Instant cursorInstant = null;

    if (cursor != null) {
      try {
        cursorInstant = Instant.parse(cursor);
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException(
            "cursor 날짜 형식이 올바르지 않습니다.", e
        );
      }
    }

    List<DirectMessage> messages;

    if (cursorInstant == null) {
      messages = directMessageRepository.findFirstPage(
          userId,
          targetUserId,
          PageRequest.of(0, limit + 1)
      );
    } else {
      messages = directMessageRepository.findNextPage(
          userId,
          targetUserId,
          cursorInstant,
          idAfter,
          PageRequest.of(0, limit + 1)
      );
    }

    boolean hasNext = messages.size() > limit;

    if (hasNext) {
      messages = messages.subList(0, limit);
    }

    Map<UUID, String> imageUrls = profileImageUrls(userId, targetUserId);
    List<DirectMessageResponse> data = messages.stream()
        .map(message -> DirectMessageResponse.of(
            message,
            imageUrls.get(message.getSender().getId()),
            imageUrls.get(message.getReceiver().getId())))
        .toList();

    DirectMessage lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);
    // 다음 페이지가 없으면 커서를 비운다
    String nextCursor = hasNext ? lastMessage.getCreatedAt().toString() : null;
    UUID nextIdAfter = hasNext ? lastMessage.getId() : null;

    long totalCount = directMessageRepository.countMessagesBetweenUsers(
        userId,
        targetUserId
    );

    return new DirectMessageCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        SORT_BY,
        SORT_DIRECTION
    );
  }

  // 대화하는 두 사람의 프로필 이미지 (프로필이 없거나 이미지가 없으면 null)
  private Map<UUID, String> profileImageUrls(UUID userId, UUID otherUserId) {
    Map<UUID, String> imageUrls = new HashMap<>();
    for (Profile profile : profileRepository.findAllById(List.of(userId, otherUserId))) {
      imageUrls.put(profile.getUserId(), profile.getProfileImageUrl());
    }
    return imageUrls;
  }
}
