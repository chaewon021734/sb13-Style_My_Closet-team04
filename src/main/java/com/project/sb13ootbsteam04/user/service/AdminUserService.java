package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.user.dto.response.UserDto;
import com.project.sb13ootbsteam04.user.entity.AdminOperationLock;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.exception.UserException;
import com.project.sb13ootbsteam04.user.repository.AdminOperationLockRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminOperationLockRepository adminOperationLockRepository;

    // ========================================
    // 1. 사용자 권한 변경
    // ========================================

    @Transactional
    public UserDto changeRole(UUID userId, UserRole newRole) {

        Objects.requireNonNull(
                userId,
                "대상 사용자 ID는 필수입니다."
        );

        Objects.requireNonNull(
                newRole,
                "변경할 사용자 권한은 필수입니다."
        );

        // 관리자 변경 작업을 직렬화하는 고정 잠금 획득
        lockAdminOperation();

        // 잠금 획득 후 대상 사용자 조회
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> UserException.userNotFound(userId));

        // 동일 권한이면 변경하지 않음
        if (user.getRole() == newRole) {
            return UserDto.from(user);
        }

        // 관리자 강등 시 다른 활성 관리자가 존재해야 함
        if (user.getRole() == UserRole.ADMIN
                && newRole == UserRole.USER) {

            boolean hasOtherActiveAdmin =
                    userRepository.findAllByRoleForUpdate(
                            UserRole.ADMIN
                    ).stream()
                    .anyMatch(admin ->
                            !userId.equals(admin.getId())
                                    && !admin.isLocked()
                                    && admin.getRole() == UserRole.ADMIN
                    );

            if (!hasOtherActiveAdmin) {
                throw new IllegalStateException(
                        "마지막 활성 관리자의 권한은 변경할 수 없습니다."
                );
            }
        }

        // User.changeRole()에서 loginVersion 증가
        user.changeRole(newRole);

        return UserDto.from(user);
    }

    // ========================================
    // 2. 사용자 계정 잠금 및 해제
    // ========================================

    @Transactional
    public UserDto changeLocked(
            UUID userId,
            boolean locked,
            UUID currentAdminId
    ) {

        Objects.requireNonNull(
                userId,
                "대상 사용자 ID는 필수입니다."
        );

        Objects.requireNonNull(
                currentAdminId,
                "현재 관리자 ID는 필수입니다."
        );

        // 자기 계정 잠금 방지
        if (locked && userId.equals(currentAdminId)) {
            throw new IllegalArgumentException(
                    "관리자는 자신의 계정을 잠글 수 없습니다."
            );
        }

        // 관리자 변경 작업을 직렬화하는 고정 잠금 획득
        lockAdminOperation();

        // 잠금 획득 후 대상 사용자 조회
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> UserException.userNotFound(userId));

        // 동일한 상태면 변경하지 않음
        if (user.isLocked() == locked) {
            return UserDto.from(user);
        }

        // 활성 관리자 잠금 시 다른 활성 관리자 존재 여부 확인
        if (locked && user.getRole() == UserRole.ADMIN) {

            boolean hasOtherActiveAdmin =
                    userRepository.findAllByRoleForUpdate(
                            UserRole.ADMIN
                    ).stream()
                    .anyMatch(admin ->
                            !userId.equals(admin.getId())
                                    && !admin.isLocked()
                                    && admin.getRole() == UserRole.ADMIN
                    );

            if (!hasOtherActiveAdmin) {
                throw new IllegalStateException(
                        "마지막 활성 관리자 계정은 잠글 수 없습니다."
                );
            }
        }

        // User.changeLocked()에서 loginVersion 증가
        user.changeLocked(locked);

        return UserDto.from(user);
    }

    // ========================================
    // 3. 관리자 변경 작업 공통 잠금
    // ========================================

    private void lockAdminOperation() {

        adminOperationLockRepository.findByIdForUpdate(
                AdminOperationLock.LOCK_ID
        ).orElseThrow(() ->
                new IllegalStateException(
                        "관리자 작업 잠금 행이 존재하지 않습니다."
                )
        );
    }
}
