
package com.project.sb13ootbsteam04.directmessage.service;

import com.project.sb13ootbsteam04.directmessage.repository.DirectMessageRepository;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceTest {

  @Mock
  private DirectMessageRepository directMessageRepository;

  @Mock
  private ProfileRepository profileRepository;

  @InjectMocks
  private DirectMessageService directMessageService;

  @Test
  void getMessages_throwsException_whenLimitIsInvalid() {
    UUID userId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    assertThatThrownBy(() ->
        directMessageService.getMessages(
            userId,
            targetUserId,
            null,
            null,
            0
        )
    ).isInstanceOf(IllegalArgumentException.class);
  }

  //
  @Test
  void getMessages_throwsException_whenCursorAndIdAfterAreNotBothPresent() {
    UUID userId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    assertThatThrownBy(() ->
        directMessageService.getMessages(
            userId,
            targetUserId,
            "2026-11-25T10:00:00Z",
            null,
            10
        )
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getMessages_throwsException_whenCursorFormatIsInvalid() {
    UUID userId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();

    assertThatThrownBy(() ->
        directMessageService.getMessages(
            userId,
            targetUserId,
            "invalid-date",
            idAfter,
            10
        )
    ).isInstanceOf(IllegalArgumentException.class);
  }
}
