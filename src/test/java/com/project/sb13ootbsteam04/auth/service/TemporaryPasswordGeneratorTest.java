package com.project.sb13ootbsteam04.auth.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator =
            new TemporaryPasswordGenerator();

    private static final String ALLOWED_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ"
                    + "abcdefghijkmnopqrstuvwxyz"
                    + "23456789"
                    + "!@#$%";

    // 1. 임시 비밀번호는 항상 12자리
    @Test
    void shouldGenerateTwelveCharacterPassword() {

        String password = generator.generate();

        assertNotNull(password);
        assertEquals(12, password.length());
    }

    // 2. 허용된 문자만 사용
    @Test
    void shouldUseOnlyAllowedCharacters() {

        for (int i = 0; i < 100; i++) {

            String password = generator.generate();

            assertEquals(12, password.length());

            for (char character : password.toCharArray()) {
                assertTrue(
                        ALLOWED_CHARACTERS.indexOf(character) >= 0,
                        "허용되지 않은 문자가 포함되어 있습니다."
                );
            }
        }
    }

    // 3. 여러 번 생성해도 동일한 값만 반환하지 않음
    @Test
    void shouldGenerateDifferentPasswords() {

        Set<String> passwords = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            passwords.add(generator.generate());
        }

        assertTrue(
                passwords.size() > 1,
                "생성된 비밀번호가 모두 동일합니다."
        );
    }
}
