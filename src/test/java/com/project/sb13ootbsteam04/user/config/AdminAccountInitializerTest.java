package com.project.sb13ootbsteam04.user.config;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminAccountInitializer initializer;

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "secureAdminPassword123";

    @BeforeEach
    void setUp() {
        initializer = new AdminAccountInitializer(
                userRepository,
                passwordEncoder,
                ADMIN_EMAIL,
                ADMIN_PASSWORD
        );
    }

    // 1. 관리자 계정이 없으면 자동 생성
    @Test
    void shouldCreateAdminWhenNotExists() {

        // given
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode(ADMIN_PASSWORD))
                .thenReturn("encodedAdminPassword");

        // when
        initializer.run(new DefaultApplicationArguments());

        // then
        verify(userRepository).saveAndFlush(argThat(user ->
                user.getRole() == UserRole.ADMIN
                        && user.getEmail().equals(ADMIN_EMAIL)
                        && user.getName().equals("관리자")
                        && user.getPassword().equals("encodedAdminPassword")
                        && !user.isLocked()
        ));
    }

    // 2. 이미 관리자 계정이 있다면 중복 생성하지 않음
    @Test
    void shouldNotCreateDuplicateAdmin() {

        // given
        User existingAdmin = User.createAdmin(
                "관리자",
                ADMIN_EMAIL,
                "existingEncodedPassword"
        );

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(existingAdmin));

        // when
        initializer.run(new DefaultApplicationArguments());

        // then
        verify(userRepository, never())
                .saveAndFlush(any(User.class));

        verifyNoInteractions(passwordEncoder);
    }

    // 3. 같은 이메일의 일반 사용자를 자동으로 관리자로 변경하지 않음
    @Test
    void shouldRejectExistingRegularUser() {

        // given
        User existingUser = new User(
                "홍길동",
                ADMIN_EMAIL,
                "encodedPassword"
        );

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(existingUser));

        // when
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments())
        );

        // then
        assertEquals(
                "초기 관리자 이메일이 일반 사용자 계정에 이미 사용 중입니다.",
                exception.getMessage()
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }

    // 4. 비밀번호가 너무 짧으면 초기화 실패
    @Test
    void shouldRejectShortAdminPassword() {

        // given
        AdminAccountInitializer invalidInitializer =
                new AdminAccountInitializer(
                        userRepository,
                        passwordEncoder,
                        ADMIN_EMAIL,
                        "short"
                );

        // when
        assertThrows(
                IllegalStateException.class,
                () -> invalidInitializer.run(new DefaultApplicationArguments())
        );

        // then
        verifyNoInteractions(userRepository, passwordEncoder);
    }
}
