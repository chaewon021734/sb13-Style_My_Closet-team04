package com.project.sb13ootbsteam04.auth.service;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TemporaryPasswordMailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    // 임시 비밀번호 이메일 발송
    public void sendTemporaryPassword(
            String receiverEmail,
            String temporaryPassword
    ) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(receiverEmail);

        message.setSubject("[Otboo] 임시 비밀번호 안내");

        message.setText(
                """
                안녕하세요. Otboo입니다.

                요청하신 임시 비밀번호를 안내드립니다.

                임시 비밀번호: %s

                임시 비밀번호는 발급 후 3분간 유효합니다.

                임시 비밀번호로 로그인한 뒤
                새로운 비밀번호로 변경해 주세요.

                본인이 요청하지 않은 경우
                이 이메일을 무시해 주세요.

                감사합니다.
                Otboo 드림
                """.formatted(temporaryPassword)
        );

        mailSender.send(message);
    }
}
