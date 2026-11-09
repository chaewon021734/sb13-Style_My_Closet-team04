package com.project.sb13ootbsteam04.auth.jwt;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAccountStatusValidatorTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtAccountStatusValidator validator;

    private User user;
    private UUID userId;
    private Jwt jwt;

    @BeforeEach
    void setUp() {

        // 테스트 사용자 생성
        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedPassword"
        );

        // 사용자 UUID 설정
        userId = UUID.randomUUID();

        ReflectionTestUtils.setField(user, "id", userId);

        // 테스트용 JWT 생성
        Instant now = Instant.now();

        jwt = Jwt.withTokenValue("test.jwt.token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", "USER")
                .build();
    }

    // 1. 정상 사용자 → JWT 검증 성공
    @Test
    void shouldAcceptActiveUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertFalse(result.hasErrors());

        verify(userRepository).findById(userId);
    }

    // 2. 잠긴 사용자 → JWT 검증 실패
    @Test
    void shouldRejectLockedUser() {

        // given
        ReflectionTestUtils.setField(user, "locked", true);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertTrue(result.hasErrors());

        assertEquals(
                "invalid_token",
                result.getErrors().iterator().next().getErrorCode()
        );

        verify(userRepository).findById(userId);
    }

    // 3. 삭제된 사용자 → JWT 검증 실패
    @Test
    void shouldRejectDeletedUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertTrue(result.hasErrors());

        verify(userRepository).findById(userId);
    }

    // 4. 잘못된 사용자 UUID → JWT 검증 실패
    @Test
    void shouldRejectInvalidUserId() {

        // given
        Jwt invalidJwt = Jwt.withTokenValue("invalid.jwt.token")
                .header("alg", "HS256")
                .subject("invalid-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(invalidJwt);

        // then
        assertTrue(result.hasErrors());

        // UUID 형식이 잘못되었으므로 DB 조회하지 않음
        verifyNoInteractions(userRepository);
    }
}
