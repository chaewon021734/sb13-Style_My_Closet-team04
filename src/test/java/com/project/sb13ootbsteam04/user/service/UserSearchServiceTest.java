
package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.user.dto.request.UserSearchRequest;
import com.project.sb13ootbsteam04.user.dto.response.UserDtoCursorResponse;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // 1. 첫 페이지 정상 조회
    @Test
    void shouldReturnFirstPageSuccessfully() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                null, null, 2,
                "email", "ASCENDING",
                null, null, null
        );

        User user1 = new User("철수", "a@test.com", "encoded1");
        User user2 = new User("영희", "b@test.com", "encoded2");

        when(userRepository.count(any(Specification.class)))
                .thenReturn(2L);

        when(userRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(List.of(user1, user2))
        );

        // when
        UserDtoCursorResponse result =
                userService.searchUsers(request);

        // then
        assertEquals(2, result.data().size());
        assertEquals("a@test.com", result.data().get(0).email());
        assertEquals("b@test.com", result.data().get(1).email());
        assertEquals(2L, result.totalCount());
        assertFalse(result.hasNext());
        assertNull(result.nextCursor());
        assertNull(result.nextIdAfter());
        assertEquals("email", result.sortBy());
        assertEquals("ASCENDING", result.sortDirection());
    }

    // 2. 다음 페이지가 있는 경우
    @Test
    void shouldReturnHasNextWhenMoreUsersExist() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                null, null, 2,
                "email", "ASCENDING",
                null, null, null
        );

        User user1 = new User("철수", "a@test.com", "pw1");
        User user2 = new User("영희", "b@test.com", "pw2");
        User user3 = new User("민수", "c@test.com", "pw3");

        when(userRepository.count(any(Specification.class)))
                .thenReturn(3L);

        when(userRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(List.of(user1, user2, user3))
        );

        // when
        UserDtoCursorResponse result =
                userService.searchUsers(request);

        // then
        assertEquals(2, result.data().size());
        assertTrue(result.hasNext());
        assertEquals("b@test.com", result.nextCursor());
        assertEquals(3L, result.totalCount());
    }

    // 3. limit이 0인 경우
    @Test
    void shouldThrowExceptionWhenLimitIsInvalid() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                null, null, 0,
                "email", "ASCENDING",
                null, null, null
        );

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        verifyNoInteractions(userRepository);
    }

    // 4. limit이 최대값 100을 초과하는 경우
    @Test
    void shouldRejectLimitOverMaximum() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                null, null, 101,
                "email", "ASCENDING",
                null, null, null
        );

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        assertEquals(
                "limit은 1 이상 100 이하여야 합니다.",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
    }

    // 5. 정수 최댓값을 limit으로 입력한 경우
    @Test
    void shouldRejectIntegerMaxValueAsLimit() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                null, null, Integer.MAX_VALUE,
                "email", "ASCENDING",
                null, null, null
        );

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        verifyNoInteractions(userRepository);
    }

    // 6. 잘못된 정렬 기준
    @Test
    void shouldThrowExceptionWhenSortByIsInvalid() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                null, null, 10,
                "invalid", "ASCENDING",
                null, null, null
        );

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        verifyNoInteractions(userRepository);
    }

    // 7. 잘못된 정렬 방향
    @Test
    void shouldThrowExceptionWhenDirectionIsInvalid() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                null, null, 10,
                "email", "INVALID",
                null, null, null
        );

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        verifyNoInteractions(userRepository);
    }

    // 8. cursor만 있고 idAfter가 없는 경우
    @Test
    void shouldThrowExceptionWhenCursorHasNoIdAfter() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                "a@test.com", null, 10,
                "email", "ASCENDING",
                null, null, null
        );

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        assertEquals(
                "cursor 사용 시 idAfter가 필요합니다.",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
    }

    // 9. idAfter만 있고 cursor가 없는 경우
    @Test
    void shouldThrowExceptionWhenIdAfterHasNoCursor() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                null,
                UUID.randomUUID(),
                10,
                "email",
                "ASCENDING",
                null,
                null,
                null
        );

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        assertEquals(
                "idAfter 사용 시 cursor가 필요합니다.",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
    }
}
