package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.user.dto.request.UserCreateRequest;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateUserSuccessfully() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "홍길동",
                "test@example.com",
                "password123"
        );

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(false);

        when(passwordEncoder.encode(request.password()))
                .thenReturn("encodedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserDto result = userService.create(request);

        // then
        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();

        assertEquals("홍길동", savedUser.getName());
        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("encodedPassword", savedUser.getPassword());
        assertEquals(UserRole.USER, savedUser.getRole());
        assertFalse(savedUser.isLocked());

        assertEquals("test@example.com", result.email());
        assertEquals("홍길동", result.name());
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "홍길동",
                "test@example.com",
                "password123"
        );

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(true);

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.create(request)
        );

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }
}
