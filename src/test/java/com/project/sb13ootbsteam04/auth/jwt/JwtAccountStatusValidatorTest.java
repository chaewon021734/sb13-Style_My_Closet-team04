package com.project.sb13ootbsteam04.auth.jwt;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
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

        // 최초 로그인 성공 상황
        user.increaseLoginVersion();

        // 로그인 버전 1을 가진 JWT 생성
        jwt = createJwt("USER", user.getLoginVersion());
    }

    // ========================================
    // 1. 정상 사용자 JWT 검증 성공
    // ========================================

    @Test
    void shouldAcceptActiveUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(jwt);

        // then
        assertFalse(result.hasErrors());

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 2. 잠긴 사용자 JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectLockedUser() {

        // given
        user.changeLocked(true);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(jwt);

        // then
        assertTrue(result.hasErrors());

        assertEquals(
                "invalid_token",
                result.getErrors().iterator().next().getErrorCode()
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 3. 삭제된 사용자 JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectDeletedUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(jwt);

        // then
        assertTrue(result.hasErrors());

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 4. 잘못된 UUID JWT 검증 실패
    // ========================================

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

        verifyNoInteractions(userRepository);
    }

    // ========================================
    // 5. JWT ADMIN 권한과 DB USER 권한 불일치
    // ========================================

    @Test
    void shouldRejectTokenWhenAdminRoleChangedToUser() {

        // given
        user.changeRole(UserRole.ADMIN);

        Jwt adminJwt = createJwt(
                "ADMIN",
                user.getLoginVersion()
        );

        // JWT 발급 이후 USER로 권한 변경
        user.changeRole(UserRole.USER);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(adminJwt);

        // then
        assertTrue(result.hasErrors());

        assertEquals(
                "invalid_token",
                result.getErrors().iterator().next().getErrorCode()
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 6. JWT와 DB 역할 일치 시 검증 성공
    // ========================================

    @Test
    void shouldAcceptTokenWhenRoleMatchesDatabase() {

        // given
        user.changeRole(UserRole.ADMIN);

        Jwt adminJwt = createJwt(
                "ADMIN",
                user.getLoginVersion()
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(adminJwt);

        // then
        assertFalse(result.hasErrors());

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 7. JWT에 role 클레임이 없는 경우
    // ========================================

    @Test
    void shouldRejectTokenWithoutRoleClaim() {

        // given
        Jwt tokenWithoutRole = createJwt(
                null,
                user.getLoginVersion()
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(tokenWithoutRole);

        // then
        assertTrue(result.hasErrors());

        assertEquals(
                "invalid_token",
                result.getErrors().iterator().next().getErrorCode()
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 8. 재로그인 이전 JWT 무효화
    // ========================================

    @Test
    void shouldRejectOldTokenAfterRelogin() {

        // given
        // 첫 번째 로그인에서 발급한 JWT (버전 1)
        Jwt oldJwt = createJwt(
                "USER",
                user.getLoginVersion()
        );

        // 두 번째 로그인 성공 → DB 버전 2
        user.increaseLoginVersion();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(oldJwt);

        // then
        assertTrue(result.hasErrors());

        assertEquals(
                "invalid_token",
                result.getErrors().iterator().next().getErrorCode()
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 9. 재로그인 후 새로운 JWT 검증 성공
    // ========================================

    @Test
    void shouldAcceptNewTokenAfterRelogin() {

        // given
        // 두 번째 로그인 → 버전 2
        user.increaseLoginVersion();

        Jwt newJwt = createJwt(
                "USER",
                user.getLoginVersion()
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(newJwt);

        // then
        assertFalse(result.hasErrors());

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 10. loginVersion 클레임 누락 시 인증 거부
    // ========================================

    @Test
    void shouldRejectTokenWithoutLoginVersion() {

        // given
        Jwt tokenWithoutVersion = createJwt(
                "USER",
                null
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(tokenWithoutVersion);

        // then
        assertTrue(result.hasErrors());

        assertEquals(
                "invalid_token",
                result.getErrors().iterator().next().getErrorCode()
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 11. loginVersion이 숫자가 아닌 경우 인증 거부
    // ========================================

    @Test
    void shouldRejectInvalidLoginVersionClaim() {

        // given
        Jwt invalidVersionJwt = createJwt(
                "USER",
                "invalid-version"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        OAuth2TokenValidatorResult result =
                validator.validate(invalidVersionJwt);

        // then
        assertTrue(result.hasErrors());

        assertEquals(
                "invalid_token",
                result.getErrors().iterator().next().getErrorCode()
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 테스트용 JWT 생성 메서드
    // ========================================

    private Jwt createJwt(String role, Object loginVersion) {

        Instant now = Instant.now();

        Jwt.Builder builder = Jwt.withTokenValue("test.jwt.token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600));

        // 역할 설정
        if (role != null) {
            builder.claim("role", role);
        }

        // 로그인 버전 설정
        if (loginVersion != null) {
            builder.claim("loginVersion", loginVersion);
        }

        return builder.build();
    }
}
