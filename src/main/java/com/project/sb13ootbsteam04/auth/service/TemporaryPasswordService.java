package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.event.TemporaryPasswordIssuedEvent;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void issueTemporaryPassword(String email) {

        // 1. 사용자 조회 및 비관적 쓰기 락 획득
        Optional<User> userOptional =
                userRepository.findByEmailForUpdate(email);

        // 2. 등록되지 않은 이메일은 발급하지 않음
        // 계정 존재 여부를 외부에 노출하지 않음
        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();

        // 3. 잠긴 계정은 발급하지 않음
        if (user.isLocked()) {
            return;
        }

        Instant now = Instant.now();

        // 4. 유효한 임시 비밀번호가 있으면 중복 발급 방지
        if (user.isTemporaryPasswordActive(now)) {
            return;
        }

        // 5. 임시 비밀번호 생성
        String temporaryPassword =
                passwordGenerator.generate();

        // 6. 임시 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(temporaryPassword);

        // 7. 만료 시간 설정 (3분)
        Instant expiresAt = now.plus(
                EXPIRATION_MINUTES,
                ChronoUnit.MINUTES
        );

        // 8. 임시 비밀번호 해시 및 만료 시간 저장
        // 기존 JWT 무효화를 위해 loginVersion 증가
        user.issueTemporaryPassword(
                encodedPassword,
                expiresAt
        );

        // 9. 이메일 발송 이벤트 발행
        // DB 커밋 성공 후 리스너에서 실제 이메일 발송
        eventPublisher.publishEvent(
                new TemporaryPasswordIssuedEvent(
                        user.getEmail(),
                        temporaryPassword,
                        encodedPassword,
                        user.getLoginVersion()
                )
        );

        // 평문 임시 비밀번호는 DB나 API 응답에 저장하지 않음
    }
}
