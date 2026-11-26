package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private UUID userId;
    private UUID currentAdminId;
    private User user;

    @BeforeEach
    void setUp() {

        userId = UUID.randomUUID();

        // 현재 로그인한 관리자는 대상 사용자와 다른 ID
        currentAdminId = UUID.randomUUID();

        user = new User(
                "홍길동",
                "test@example.com",
                "encodedPassword"
        );
    }

    // ========================================
    // 1. USER -> ADMIN 권한 변경
    // ========================================

    @Test
    void shouldChangeUserRoleToAdmin() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        adminUserService.changeRole(userId, UserRole.ADMIN);

        // then
        assertEquals(UserRole.ADMIN, user.getRole());

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 2. ADMIN -> USER 권한 변경
    // ========================================

    @Test
    void shouldChangeAdminRoleToUser() {

        // given
        User admin = User.createAdmin(
                "관리자",
                "admin@example.com",
                "encodedPassword"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(admin));

        // 관리자가 2명 존재하므로 권한 강등 가능
        when(userRepository.countByRole(UserRole.ADMIN))
                .thenReturn(2L);

        // when
        adminUserService.changeRole(userId, UserRole.USER);

        // then
        assertEquals(UserRole.USER, admin.getRole());

        verify(userRepository).countByRole(UserRole.ADMIN);
    }

    // ========================================
    // 3. 사용자 계정 잠금
    // ========================================

    @Test
    void shouldLockUserAccount() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        adminUserService.changeLocked(
                userId,
                true,
                currentAdminId
        );

        // then
        assertTrue(user.isLocked());

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 4. 잠긴 계정 해제
    // ========================================

    @Test
    void shouldUnlockUserAccount() {

        // given
        user.changeLocked(true);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        adminUserService.changeLocked(
                userId,
                false,
                currentAdminId
        );

        // then
        assertFalse(user.isLocked());

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 5. 존재하지 않는 사용자 권한 변경
    // ========================================

    @Test
    void shouldRejectRoleChangeForUnknownUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminUserService.changeRole(
                        userId,
                        UserRole.ADMIN
                )
        );

        // then
        assertEquals(
                "존재하지 않는 사용자입니다.",
                exception.getMessage()
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 6. 존재하지 않는 사용자 계정 잠금
    // ========================================

    @Test
    void shouldRejectLockForUnknownUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminUserService.changeLocked(
                        userId,
                        true,
                        currentAdminId
                )
        );

        // then
        assertEquals(
                "존재하지 않는 사용자입니다.",
                exception.getMessage()
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 7. 마지막 관리자 권한 강등 방지
    // ========================================

    @Test
    void shouldRejectDemotingLastAdmin() {

        // given
        User admin = User.createAdmin(
                "관리자",
                "admin@example.com",
                "encodedPassword"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(admin));

        when(userRepository.countByRole(UserRole.ADMIN))
                .thenReturn(1L);

        // when
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> adminUserService.changeRole(
                        userId,
                        UserRole.USER
                )
        );

        // then
        assertEquals(
                "마지막 관리자의 권한은 변경할 수 없습니다.",
                exception.getMessage()
        );

        // 관리자 권한이 그대로 유지
        assertEquals(UserRole.ADMIN, admin.getRole());

        verify(userRepository).countByRole(UserRole.ADMIN);
    }

    // ========================================
    // 8. 관리자가 2명 이상이면 권한 강등 허용
    // ========================================

    @Test
    void shouldAllowDemotingAdminWhenMultipleAdminsExist() {

        // given
        User admin = User.createAdmin(
                "관리자",
                "admin@example.com",
                "encodedPassword"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(admin));

        when(userRepository.countByRole(UserRole.ADMIN))
                .thenReturn(2L);

        // when
        adminUserService.changeRole(
                userId,
                UserRole.USER
        );

        // then
        assertEquals(UserRole.USER, admin.getRole());

        verify(userRepository).countByRole(UserRole.ADMIN);
    }

    // ========================================
    // 9. 관리자가 자기 계정을 잠그는 경우 차단
    // ========================================

    @Test
    void shouldRejectAdminLockingOwnAccount() {

        // given
        UUID ownAccountId = currentAdminId;

        // when
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adminUserService.changeLocked(
                        ownAccountId,
                        true,
                        currentAdminId
                )
        );

        // then
        assertEquals(
                "관리자는 자신의 계정을 잠글 수 없습니다.",
                exception.getMessage()
        );

        // DB 조회 전에 차단
        verifyNoInteractions(userRepository);
    }
}
