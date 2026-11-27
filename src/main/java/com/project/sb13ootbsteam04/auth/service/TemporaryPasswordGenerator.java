package com.project.sb13ootbsteam04.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class TemporaryPasswordGenerator {

    private static final String CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ"
                    + "abcdefghijkmnopqrstuvwxyz"
                    + "23456789"
                    + "!@#$%";

    private static final int PASSWORD_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    // 12자리 임시 비밀번호 생성
    public String generate() {

        StringBuilder password =
                new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {

            int index = secureRandom.nextInt(
                    CHARACTERS.length()
            );

            password.append(CHARACTERS.charAt(index));
        }

        return password.toString();
    }
}
