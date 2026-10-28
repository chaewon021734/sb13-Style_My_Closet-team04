
package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.user.dto.request.UserCreateRequest;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.exception.DuplicateEmailException;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

        // 이메일 중복 없음
        when(userRepository.existsByEmail(request.email()))
                .thenReturn(false);

        // 비밀번호 암호화
        when(passwordEncoder.encode(request.password()))
                .thenReturn("encodedPassword");

        // saveAndFlush() 호출 시 전달받은 User 반환
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserDto result = userService.create(request);

        // then
        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        // saveAndFlush() 호출 검증
        verify(userRepository).saveAndFlush(captor.capture());

        User savedUser = captor.getValue();

        // 사용자 정보 확인
        assertEquals("홍길동", savedUser.getName());
        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("encodedPassword", savedUser.getPassword());

        // 기본 권한 및 잠금 상태 확인
        assertEquals(UserRole.USER, savedUser.getRole());
        assertFalse(savedUser.isLocked());

        // 응답 DTO 확인
        assertEquals("test@example.com", result.email());
        assertEquals("홍길동", result.name());

        // 비밀번호 암호화 호출 확인
        verify(passwordEncoder).encode("password123");

        // 일반 save()는 호출되지 않아야 함
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {

        // given
        UserCreateRequest request = new UserCreateRequest(
                "홍길동",
                "test@example.com",
                "password123"
        );

        // 이미 존재하는 이메일
        when(userRepository.existsByEmail(request.email()))
                .thenReturn(true);

        // when & then
        DuplicateEmailException exception = assertThrows(
                DuplicateEmailException.class,
                () -> userService.create(request)
        );

        // 예외 메시지 확인
        assertEquals(
                "이미 사용 중인 이메일입니다.",
                exception.getMessage()
        );

        // 이메일 중복 검사 호출 확인
        verify(userRepository).existsByEmail("test@example.com");

        // 중복 이메일이면 DB 저장하지 않음
        verify(userRepository, never())
                .saveAndFlush(any(User.class));

        verify(userRepository, never())
                .save(any(User.class));

        // 비밀번호 암호화도 실행하지 않음
        verifyNoInteractions(passwordEncoder);
    }
}
