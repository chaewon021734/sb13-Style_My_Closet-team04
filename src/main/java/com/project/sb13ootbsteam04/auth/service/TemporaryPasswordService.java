package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TemporaryPasswordService {

    private static final long EXPIRATION_MINUTES = 3;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;

    // 이메일 발송 Service
    private final TemporaryPasswordMailService mailService;

    @Transactional
    public void issueTemporaryPassword(String email) {

        // 1. 사용자 조회 및 비관적 쓰기 락 획득
        Optional<User> userOptional =
                userRepository.findByEmailForUpdate(email);

        // 2. 등록되지 않은 이메일은 발급하지 않음
        // 외부 API에서는 등록 여부와 관계없이 동일한 응답 반환
        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();

        // 3. 잠긴 계정은 발급하지 않음
        if (user.isLocked()) {
            return;
        }

        // 4. 현재 시간 확인
        Instant now = Instant.now();

        // 5. 기존 임시 비밀번호가 아직 유효하다면
        // 새로운 임시 비밀번호를 발급하지 않음
        if (user.isTemporaryPasswordActive(now)) {
            return;
        }

        // 6. 임시 비밀번호 생성
        String temporaryPassword =
                passwordGenerator.generate();

        // 7. 임시 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(temporaryPassword);

        // 8. 만료 시간 설정 (현재 시간으로부터 3분)
        Instant expiresAt = now.plus(
                EXPIRATION_MINUTES,
                ChronoUnit.MINUTES
        );

        // 9. 암호화된 임시 비밀번호와 만료 시간 저장
        // 기존 JWT 무효화를 위해 loginVersion 증가
        user.issueTemporaryPassword(
                encodedPassword,
                expiresAt
        );

        // 10. 등록된 이메일로 임시 비밀번호 발송
        mailService.sendTemporaryPassword(
                user.getEmail(),
                temporaryPassword
        );

        // 평문 임시 비밀번호는 반환하지 않음
    }
}
