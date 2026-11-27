package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String RESET_FAILURE_MESSAGE =
            "비밀번호를 재설정할 수 없습니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void resetPassword(UUID userId, String newPassword) {

        // 1. 사용자 조회 및 비관적 쓰기 락 획득
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                RESET_FAILURE_MESSAGE
                        )
                );

        // 2. 잠긴 계정은 변경 불가
        if (user.isLocked()) {
            throw new BadCredentialsException(
                    RESET_FAILURE_MESSAGE
            );
        }

        // 3. 유효한 임시 비밀번호 확인
        if (!user.isTemporaryPasswordActive(Instant.now())) {
            throw new BadCredentialsException(
                    "임시 비밀번호가 만료되었거나 유효하지 않습니다."
            );
        }

        // 4. 새 비밀번호 검증
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "새 비밀번호는 필수입니다."
            );
        }

        // 5. 새 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(newPassword);

        // 6. 비밀번호 변경 + 임시 비밀번호 제거
        // User.changePassword()에서 loginVersion 증가
        user.changePassword(encodedPassword);
    }
}
