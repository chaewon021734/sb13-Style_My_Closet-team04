package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemporaryPasswordRecoveryService {

    private final UserRepository userRepository;

    /**
     * SMTP 발송 실패 시 임시 비밀번호를 무효화한다.
     *
     * DB 커밋 이후 실행되므로 새로운 트랜잭션을 사용한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recover(
            String email,
            String expectedPasswordHash,
            long expectedLoginVersion
    ) {

        // 1. 사용자 조회 및 비관적 쓰기 락 획득
        User user = userRepository.findByEmailForUpdate(email)
                .orElse(null);

        if (user == null) {
            return;
        }

        // 2. 다른 로그인이나 계정 변경이 있었다면 복구하지 않음
        if (user.getLoginVersion() != expectedLoginVersion) {
            return;
        }

        // 3. 발급 당시의 임시 비밀번호와 일치하는지 확인
        if (expectedPasswordHash == null
                || !expectedPasswordHash.equals(
                        user.getTemporaryPasswordHash()
                )) {
            return;
        }

        // 4. 현재 임시 비밀번호 정보 삭제
        user.clearTemporaryPassword();

        // 5. 해당 임시 비밀번호로 발급한 JWT 무효화
        user.increaseLoginVersion();
    }
}
