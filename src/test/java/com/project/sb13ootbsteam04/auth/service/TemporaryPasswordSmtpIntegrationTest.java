package com.project.sb13ootbsteam04.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileInputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class TemporaryPasswordSmtpIntegrationTest {

    @Test
    void shouldSendRealEmail() throws Exception {

        // 실제 메일 발송을 명시적으로 허용한 경우에만 실행
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(
                        System.getenv("RUN_SMTP_TEST")
                )
        );

        // 1. 프로젝트 루트의 .env 읽기
        Properties env = new Properties();

        try (FileInputStream input =
                     new FileInputStream(".env")) {
            env.load(input);
        }

        String username =
                env.getProperty("MAIL_USERNAME");

        String password =
                env.getProperty("MAIL_PASSWORD");

        assertNotNull(username);
        assertFalse(username.isBlank());

        assertNotNull(password);
        assertFalse(password.isBlank());

        // 2. Gmail SMTP 설정
        JavaMailSenderImpl mailSender =
                new JavaMailSenderImpl();

        mailSender.setHost(
                env.getProperty("MAIL_HOST", "smtp.gmail.com")
        );

        mailSender.setPort(
                Integer.parseInt(
                        env.getProperty("MAIL_PORT", "587")
                )
        );

        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties mailProperties =
                mailSender.getJavaMailProperties();

        mailProperties.put(
                "mail.smtp.auth", "true"
        );

        mailProperties.put(
                "mail.smtp.starttls.enable", "true"
        );

        mailProperties.put(
                "mail.smtp.starttls.required", "true"
        );

        mailProperties.put(
                "mail.smtp.connectiontimeout", "5000"
        );

        mailProperties.put(
                "mail.smtp.timeout", "5000"
        );

        mailProperties.put(
                "mail.smtp.writetimeout", "5000"
        );

        // 3. 기존 이메일 발송 Service 사용
        TemporaryPasswordMailService mailService =
                new TemporaryPasswordMailService(mailSender);

        ReflectionTestUtils.setField(
                mailService,
                "senderEmail",
                username
        );

        // 4. 테스트 이메일 발송
        // 실제 임시 비밀번호가 아닌 테스트용 문자열
        mailService.sendTemporaryPassword(
                username,
                "SMTP-TEST-NOT-A-REAL-PASSWORD"
        );
    }
}
