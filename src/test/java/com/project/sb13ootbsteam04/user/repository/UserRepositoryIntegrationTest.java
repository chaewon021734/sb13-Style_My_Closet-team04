
package com.project.sb13ootbsteam04.user.repository;

import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.user.entity.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    // 사용자 저장 및 UUID, 생성일, 수정일 검증
    @Test
    void shouldSaveUserWithIdAndTimestamps() {

        // given
        String email = UUID.randomUUID() + "@test.com";

        User user = new User(
                "테스트사용자",
                email,
                "encodedPassword"
        );

        // when
        User savedUser = userRepository.saveAndFlush(user);

        // then
        assertNotNull(savedUser.getId());
        assertNotNull(savedUser.getCreatedAt());
        assertNotNull(savedUser.getUpdatedAt());

        assertEquals(email, savedUser.getEmail());
        assertEquals("테스트사용자", savedUser.getName());
        assertFalse(savedUser.isLocked());
    }

    // 동일 이메일 저장 시 DB 유니크 제약조건 검증
    @Test
    void shouldRejectDuplicateEmail() {

        // given
        String email = UUID.randomUUID() + "@test.com";

        User firstUser = new User(
                "첫번째",
                email,
                "encodedPassword1"
        );

        User secondUser = new User(
                "두번째",
                email,
                "encodedPassword2"
        );

        // when
        userRepository.saveAndFlush(firstUser);

        // then
        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(secondUser)
        );
    }
}
