package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    // ========================================
    // 사용자 권한 변경
    // ========================================

    @Transactional
    public void changeRole(UUID userId, UserRole newRole) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 사용자입니다."
                        )
                );

        // 마지막 관리자 권한 강등 방지
        if (user.getRole() == UserRole.ADMIN
                && newRole == UserRole.USER) {

            long adminCount = userRepository.countByRole(UserRole.ADMIN);

            if (adminCount <= 1) {
                throw new IllegalStateException(
                        "마지막 관리자의 권한은 변경할 수 없습니다."
                );
            }
        }

        user.changeRole(newRole);
    }

    // ========================================
    // 사용자 계정 잠금 및 해제
    // ========================================

    @Transactional
    public void changeLocked(
            UUID userId,
            boolean locked,
            UUID currentAdminId
    ) {

        // 1. 관리자가 자기 계정을 잠그는 경우 차단
        if (locked && userId.equals(currentAdminId)) {
            throw new IllegalArgumentException(
                    "관리자는 자신의 계정을 잠글 수 없습니다."
            );
        }

        // 2. 대상 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 사용자입니다."
                        )
                );

        // 3. 계정 잠금 또는 해제
        user.changeLocked(locked);
    }
}
