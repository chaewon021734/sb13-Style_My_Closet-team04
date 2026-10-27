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

    @Test
    void shouldReturnFirstPageSuccessfully() {

        // given
        UserSearchRequest request = new UserSearchRequest(
                null,
                null,
                2,
                "email",
                "ASCENDING",
                null,
                null,
                null
        );

        User user1 = new User(
                "철수", "a@test.com", "encoded1"
        );

        User user2 = new User(
                "영희", "b@test.com", "encoded2"
        );

        when(userRepository.count(any(Specification.class)))
                .thenReturn(2L);

        when(userRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(user1, user2)));

        // when
        UserDtoCursorResponse result =
                userService.searchUsers(request);

        // then
        assertEquals(2, result.data().size());
        assertEquals("a@test.com", result.data().get(0).email());
        assertEquals("b@test.com", result.data().get(1).email());
        assertEquals(2L, result.totalCount());
        assertFalse(result.hasNext());
        assertEquals("email", result.sortBy());
        assertEquals("ASCENDING", result.sortDirection());
    }

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

    @Test
    void shouldThrowExceptionWhenLimitIsInvalid() {

        UserSearchRequest request = new UserSearchRequest(
                null, null, 0,
                "email", "ASCENDING",
                null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldThrowExceptionWhenSortByIsInvalid() {

        UserSearchRequest request = new UserSearchRequest(
                null, null, 10,
                "invalid", "ASCENDING",
                null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldThrowExceptionWhenDirectionIsInvalid() {

        UserSearchRequest request = new UserSearchRequest(
                null, null, 10,
                "email", "INVALID",
                null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(request)
        );

        verifyNoInteractions(userRepository);
    }
}
