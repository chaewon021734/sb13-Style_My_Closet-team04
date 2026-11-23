package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class TemporaryPasswordService {

    private static final long EXPIRATION_MINUTES = 3;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;

    @Transactional
    public String issueTemporaryPassword(String email) {

        // 1. 사용자 조회 및 비관적 쓰기 락 획득
        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 사용자입니다."
                        )
                );

        // 2. 임시 비밀번호 생성
        String temporaryPassword =
                passwordGenerator.generate();

        // 3. 임시 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(temporaryPassword);

        // 4. 만료 시간 설정 (현재 시각 + 3분)
        Instant expiresAt = Instant.now()
                .plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        // 5. 암호화된 임시 비밀번호 및 만료 시간 저장
        // User 내부에서 loginVersion도 1 증가
        user.issueTemporaryPassword(
                encodedPassword,
                expiresAt
        );

        // 6. 현재는 내부 테스트용으로 평문 반환
        // 실제 API 응답에는 노출하지 않아야 함
        return temporaryPassword;
    }
}
