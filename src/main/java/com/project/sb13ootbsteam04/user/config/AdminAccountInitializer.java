package com.project.sb13ootbsteam04.user.config;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.admin",
        name = "bootstrap-enabled",
        havingValue = "true"
)
public class AdminAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // 1. 초기 관리자 설정 확인
        if (adminEmail.isBlank() || adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "초기 관리자 이메일과 12자 이상의 비밀번호가 필요합니다."
            );
        }

        // 2. 관리자 이메일로 기존 계정 조회
        var existingUser = userRepository.findByEmail(adminEmail);

        if (existingUser.isPresent()) {

            // 이미 관리자라면 중복 생성하지 않음
            if (existingUser.get().getRole() == UserRole.ADMIN) {
                log.info("초기 관리자 계정이 이미 존재합니다.");
                return;
            }

            // 일반 사용자 계정을 자동으로 승격하지 않음
            throw new IllegalStateException(
                    "초기 관리자 이메일이 일반 사용자 계정에 이미 사용 중입니다."
            );
        }

        // 3. 관리자 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(adminPassword);

        // 4. 관리자 계정 생성
        User admin = User.createAdmin(
                "관리자",
                adminEmail,
                encodedPassword
        );

        // 5. DB 저장
        userRepository.saveAndFlush(admin);

        log.info("초기 관리자 계정이 생성되었습니다.");
    }
}
