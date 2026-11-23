package com.project.sb13ootbsteam04.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemporaryPasswordGeneratorTest {

    private TemporaryPasswordGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TemporaryPasswordGenerator();
    }

    // 1. 임시 비밀번호가 정상적으로 생성되는지 확인
    @Test
    void shouldGenerateTemporaryPassword() {

        // when
        String password = generator.generate();

        // then
        assertNotNull(password);
        assertFalse(password.isBlank());
    }

    // 2. 임시 비밀번호 길이가 12자인지 확인
    @Test
    void shouldGeneratePasswordWithLength12() {

        // when
        String password = generator.generate();

        // then
        assertEquals(12, password.length());
    }

    // 3. 허용된 문자만 사용하는지 확인
    @Test
    void shouldGeneratePasswordWithAllowedCharacters() {

        // given
        String allowedCharacters =
                "ABCDEFGHJKLMNPQRSTUVWXYZ"
                        + "abcdefghijkmnopqrstuvwxyz"
                        + "23456789"
                        + "!@#$%";

        // when
        String password = generator.generate();

        // then
        for (char character : password.toCharArray()) {
            assertTrue(
                    allowedCharacters.indexOf(character) >= 0,
                    "허용되지 않은 문자가 포함되었습니다: " + character
            );
        }
    }

    // 4. 여러 번 생성했을 때 동일한 값만 나오지 않는지 확인
    @Test
    void shouldGenerateDifferentPasswords() {

        // given
        Set<String> passwords = new HashSet<>();

        // when
        for (int i = 0; i < 100; i++) {
            passwords.add(generator.generate());
        }

        // then
        assertTrue(
                passwords.size() > 1,
                "임시 비밀번호가 모두 동일하게 생성되었습니다."
        );
    }
}
