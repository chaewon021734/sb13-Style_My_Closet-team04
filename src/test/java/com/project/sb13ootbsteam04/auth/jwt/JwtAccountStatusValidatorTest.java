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

        // 정상 JWT에는 로그인 버전이 포함되어야 함
        jwt = createJwt("USER", 0L);
    }

    // ========================================
    // 1. 정상 사용자 → JWT 검증 성공
    // ========================================

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

    // ========================================
    // 2. 잠긴 사용자 → JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectLockedUser() {

        // given
        ReflectionTestUtils.setField(user, "locked", true);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertInvalidToken(result);

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 3. 삭제된 사용자 → JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectDeletedUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        // then
        assertInvalidToken(result);

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 4. 잘못된 사용자 UUID → JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectInvalidUserId() {

        // given
        Jwt invalidJwt = Jwt.withTokenValue("invalid.jwt.token")
                .header("alg", "HS256")
                .subject("invalid-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("role", "USER")
                .claim("loginVersion", 0L)
                .build();

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(invalidJwt);

        // then
        assertInvalidToken(result);

        // UUID가 잘못되었으므로 DB를 조회하지 않음
        verifyNoInteractions(userRepository);
    }

    // ========================================
    // 5. JWT와 DB 로그인 버전 일치 → 검증 성공
    // ========================================

    @Test
    void shouldAcceptMatchingLoginVersion() {

        // given
        user.increaseLoginVersion();

        Jwt currentJwt = createJwt("USER", 1L);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(currentJwt);

        // then
        assertFalse(result.hasErrors());
        assertEquals(1L, user.getLoginVersion());

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 6. 재로그인 후 기존 JWT → 검증 실패
    // ========================================

    @Test
    void shouldRejectOldJwtAfterRelogin() {

        // given
        user.increaseLoginVersion();

        Jwt firstJwt = createJwt("USER", 1L);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // 첫 번째 로그인 직후에는 유효해야 함
        assertFalse(validator.validate(firstJwt).hasErrors());

        // 재로그인으로 DB 로그인 버전 증가
        user.increaseLoginVersion();

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(firstJwt);

        // then
        assertEquals(2L, user.getLoginVersion());
        assertInvalidToken(result);

        verify(userRepository, times(2)).findById(userId);
    }

    // ========================================
    // 7. loginVersion 없는 JWT → 검증 실패
    // ========================================

    @Test
    void shouldRejectJwtWithoutLoginVersion() {

        // given
        Instant now = Instant.now();

        Jwt missingVersionJwt = Jwt.withTokenValue("missing.version.token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", "USER")
                .build();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(missingVersionJwt);

        // then
        assertInvalidToken(result);

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 8. JWT 역할과 DB 역할 불일치 → 검증 실패
    // ========================================

    @Test
    void shouldRejectMismatchedRole() {

        // given
        Jwt adminRoleJwt = createJwt("ADMIN", 0L);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(adminRoleJwt);

        // then
        assertInvalidToken(result);

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 테스트용 JWT 생성
    // ========================================

    private Jwt createJwt(String role, long loginVersion) {

        Instant now = Instant.now();

        return Jwt.withTokenValue("test.jwt.token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", role)
                .claim("loginVersion", loginVersion)
                .build();
    }

    // ========================================
    // 잘못된 JWT 검증 결과 확인
    // ========================================

    private void assertInvalidToken(OAuth2TokenValidatorResult result) {

        assertTrue(result.hasErrors());

        assertEquals(
                "invalid_token",
                result.getErrors().iterator().next().getErrorCode()
        );
    }
}
