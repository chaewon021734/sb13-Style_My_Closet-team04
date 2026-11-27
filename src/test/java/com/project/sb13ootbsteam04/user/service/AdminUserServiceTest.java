package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.user.entity.AdminOperationLock;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.repository.AdminOperationLockRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminOperationLockRepository lockRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        // 대부분의 테스트에서 고정 잠금 행이 존재한다고 가정
        lenient().when(lockRepository.findByIdForUpdate(
                AdminOperationLock.LOCK_ID
        )).thenReturn(
                Optional.of(new AdminOperationLock(
                        AdminOperationLock.LOCK_ID
                ))
        );
    }

    // 테스트용 관리자 엔티티 생성
    private User createAdmin(UUID id, String email) {

        User admin = User.createAdmin(
                "관리자", email, "encoded"
        );

        ReflectionTestUtils.setField(admin, "id", id);

        return admin;
    }

    // ========================================
    // 1. 일반 사용자를 관리자로 승격
    // ========================================

    @Test
    void shouldPromoteUserToAdmin() {

        User user = new User(
                "사용자", "user@example.com", "encoded"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        adminUserService.changeRole(userId, UserRole.ADMIN);

        assertEquals(UserRole.ADMIN, user.getRole());
        assertEquals(1L, user.getLoginVersion());

        // 고정 잠금 이후 대상 사용자 조회
        InOrder order = inOrder(
                lockRepository, userRepository
        );

        order.verify(lockRepository)
                .findByIdForUpdate(AdminOperationLock.LOCK_ID);

        order.verify(userRepository)
                .findByIdForUpdate(userId);
    }

    // ========================================
    // 2. 다른 활성 관리자가 있으면 강등 가능
    // ========================================

    @Test
    void shouldDemoteAdminWhenAnotherActiveAdminExists() {

        User target = createAdmin(
                userId, "admin@example.com"
        );

        User otherAdmin = createAdmin(
                UUID.randomUUID(), "other@example.com"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(target));

        when(userRepository.findAllByRoleForUpdate(UserRole.ADMIN))
                .thenReturn(List.of(target, otherAdmin));

        adminUserService.changeRole(userId, UserRole.USER);

        assertEquals(UserRole.USER, target.getRole());
        assertEquals(1L, target.getLoginVersion());
    }

    // ========================================
    // 3. 마지막 활성 관리자 강등 방지
    // ========================================

    @Test
    void shouldRejectDemotingLastActiveAdmin() {

        User target = createAdmin(
                userId, "admin@example.com"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(target));

        when(userRepository.findAllByRoleForUpdate(UserRole.ADMIN))
                .thenReturn(List.of(target));

        assertThrows(
                IllegalStateException.class,
                () -> adminUserService.changeRole(
                        userId, UserRole.USER
                )
        );

        assertEquals(UserRole.ADMIN, target.getRole());
        assertEquals(0L, target.getLoginVersion());
    }

    // ========================================
    // 4. 다른 관리자가 모두 잠겼으면 강등 방지
    // ========================================

    @Test
    void shouldRejectDemotionWhenOtherAdminsAreLocked() {

        User target = createAdmin(
                userId, "admin@example.com"
        );

        User otherAdmin = createAdmin(
                UUID.randomUUID(), "other@example.com"
        );

        otherAdmin.changeLocked(true);

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(target));

        when(userRepository.findAllByRoleForUpdate(UserRole.ADMIN))
                .thenReturn(List.of(target, otherAdmin));

        assertThrows(
                IllegalStateException.class,
                () -> adminUserService.changeRole(
                        userId, UserRole.USER
                )
        );

        assertEquals(UserRole.ADMIN, target.getRole());
        assertEquals(0L, target.getLoginVersion());
    }

    // ========================================
    // 5. 같은 권한이면 버전 변경 없음
    // ========================================

    @Test
    void shouldNotIncreaseVersionForSameRole() {

        User user = new User(
                "사용자", "user@example.com", "encoded"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        adminUserService.changeRole(userId, UserRole.USER);

        assertEquals(UserRole.USER, user.getRole());
        assertEquals(0L, user.getLoginVersion());

        verify(userRepository, never())
                .findAllByRoleForUpdate(any());
    }

    // ========================================
    // 6. 존재하지 않는 사용자 권한 변경 방지
    // ========================================

    @Test
    void shouldRejectRoleChangeForMissingUser() {

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> adminUserService.changeRole(
                        userId, UserRole.ADMIN
                )
        );
    }

    // ========================================
    // 7. 일반 사용자 잠금
    // ========================================

    @Test
    void shouldLockRegularUser() {

        User user = new User(
                "사용자", "user@example.com", "encoded"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        adminUserService.changeLocked(
                userId, true, UUID.randomUUID()
        );

        assertTrue(user.isLocked());
        assertEquals(1L, user.getLoginVersion());

        verify(lockRepository)
                .findByIdForUpdate(AdminOperationLock.LOCK_ID);
    }

    // ========================================
    // 8. 일반 사용자 잠금 해제
    // ========================================

    @Test
    void shouldUnlockRegularUser() {

        User user = new User(
                "사용자", "user@example.com", "encoded"
        );

        user.changeLocked(true);
        long previousVersion = user.getLoginVersion();

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        adminUserService.changeLocked(
                userId, false, UUID.randomUUID()
        );

        assertFalse(user.isLocked());

        assertEquals(
                previousVersion + 1,
                user.getLoginVersion()
        );
    }

    // ========================================
    // 9. 관리자 자기 계정 잠금 방지
    // ========================================

    @Test
    void shouldRejectSelfLock() {

        assertThrows(
                IllegalArgumentException.class,
                () -> adminUserService.changeLocked(
                        userId, true, userId
                )
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(lockRepository);
    }

    // ========================================
    // 10. 마지막 활성 관리자 잠금 방지
    // ========================================

    @Test
    void shouldRejectLockingLastActiveAdmin() {

        User target = createAdmin(
                userId, "admin@example.com"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(target));

        when(userRepository.findAllByRoleForUpdate(UserRole.ADMIN))
                .thenReturn(List.of(target));

        assertThrows(
                IllegalStateException.class,
                () -> adminUserService.changeLocked(
                        userId, true, UUID.randomUUID()
                )
        );

        assertFalse(target.isLocked());
        assertEquals(0L, target.getLoginVersion());
    }

    // ========================================
    // 11. 다른 활성 관리자가 있으면 잠금 가능
    // ========================================

    @Test
    void shouldLockAdminWhenAnotherActiveAdminExists() {

        User target = createAdmin(
                userId, "admin@example.com"
        );

        User otherAdmin = createAdmin(
                UUID.randomUUID(), "other@example.com"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(target));

        when(userRepository.findAllByRoleForUpdate(UserRole.ADMIN))
                .thenReturn(List.of(target, otherAdmin));

        adminUserService.changeLocked(
                userId, true, UUID.randomUUID()
        );

        assertTrue(target.isLocked());
        assertEquals(1L, target.getLoginVersion());
    }

    // ========================================
    // 12. 다른 관리자가 잠겨 있으면 잠금 방지
    // ========================================

    @Test
    void shouldRejectLockingAdminWhenOthersAreLocked() {

        User target = createAdmin(
                userId, "admin@example.com"
        );

        User otherAdmin = createAdmin(
                UUID.randomUUID(), "other@example.com"
        );

        otherAdmin.changeLocked(true);

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(target));

        when(userRepository.findAllByRoleForUpdate(UserRole.ADMIN))
                .thenReturn(List.of(target, otherAdmin));

        assertThrows(
                IllegalStateException.class,
                () -> adminUserService.changeLocked(
                        userId, true, UUID.randomUUID()
                )
        );

        assertFalse(target.isLocked());
        assertEquals(0L, target.getLoginVersion());
    }

    // ========================================
    // 13. 같은 잠금 상태면 버전 변경 없음
    // ========================================

    @Test
    void shouldNotIncreaseVersionForSameLockState() {

        User user = new User(
                "사용자", "user@example.com", "encoded"
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        adminUserService.changeLocked(
                userId, false, UUID.randomUUID()
        );

        assertFalse(user.isLocked());
        assertEquals(0L, user.getLoginVersion());

        verify(userRepository, never())
                .findAllByRoleForUpdate(any());
    }

    // ========================================
    // 14. 존재하지 않는 사용자 잠금 방지
    // ========================================

    @Test
    void shouldRejectLockingMissingUser() {

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> adminUserService.changeLocked(
                        userId, true, UUID.randomUUID()
                )
        );
    }

    // ========================================
    // 15. 고정 잠금 행이 없으면 관리자 작업 중단
    // ========================================

    @Test
    void shouldRejectOperationWhenLockRowIsMissing() {

        when(lockRepository.findByIdForUpdate(
                AdminOperationLock.LOCK_ID
        )).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> adminUserService.changeRole(
                        userId, UserRole.ADMIN
                )
        );

        assertEquals(
                "관리자 작업 잠금 행이 존재하지 않습니다.",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
    }
}
