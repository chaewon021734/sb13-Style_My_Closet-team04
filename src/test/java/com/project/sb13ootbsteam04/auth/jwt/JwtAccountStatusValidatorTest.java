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

        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedPassword"
        );

        userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);

        // 최초 로그인 성공 상황
        user.increaseLoginVersion();

        // 기존 JWT 호환성 테스트용
        jwt = createJwt("USER", user.getLoginVersion());
    }

    // ========================================
    // 1. 정상 사용자 JWT 검증 성공
    // ========================================

    @Test
    void shouldAcceptActiveUser() {

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(jwt);

        assertFalse(result.hasErrors());

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 2. 잠긴 사용자 JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectLockedUser() {

        user.changeLocked(true);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(jwt);

        assertInvalidToken(result);

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 3. 삭제된 사용자 JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectDeletedUser() {

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        OAuth2TokenValidatorResult result =
                validator.validate(jwt);

        assertInvalidToken(result);

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 4. 잘못된 UUID JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectInvalidUserId() {

        Instant now = Instant.now();

        Jwt invalidJwt = Jwt.withTokenValue("invalid.jwt.token")
                .header("alg", "HS256")
                .subject("invalid-uuid")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        OAuth2TokenValidatorResult result =
                validator.validate(invalidJwt);

        assertInvalidToken(result);

        verifyNoInteractions(userRepository);
    }

    // ========================================
    // 5. JWT ADMIN과 DB USER 역할 불일치
    // ========================================

    @Test
    void shouldRejectTokenWhenAdminRoleChangedToUser() {

        user.changeRole(UserRole.ADMIN);

        Jwt adminJwt = createJwt(
                "ADMIN",
                user.getLoginVersion()
        );

        user.changeRole(UserRole.USER);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(adminJwt);

        assertInvalidToken(result);
    }

    // ========================================
    // 6. JWT와 DB 역할 일치 시 검증 성공
    // ========================================

    @Test
    void shouldAcceptTokenWhenRoleMatchesDatabase() {

        user.changeRole(UserRole.ADMIN);

        Jwt adminJwt = createJwt(
                "ADMIN",
                user.getLoginVersion()
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(adminJwt);

        assertFalse(result.hasErrors());
    }

    // ========================================
    // 7. role 클레임 누락 시 인증 거부
    // ========================================

    @Test
    void shouldRejectTokenWithoutRoleClaim() {

        Jwt tokenWithoutRole = createJwt(
                null,
                user.getLoginVersion()
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(tokenWithoutRole);

        assertInvalidToken(result);
    }

    // ========================================
    // 8. 재로그인 이전 JWT 무효화
    // ========================================

    @Test
    void shouldRejectOldTokenAfterRelogin() {

        Jwt oldJwt = createJwt(
                "USER",
                user.getLoginVersion()
        );

        user.increaseLoginVersion();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(oldJwt);

        assertInvalidToken(result);
    }

    // ========================================
    // 9. 재로그인 후 새 JWT 검증 성공
    // ========================================

    @Test
    void shouldAcceptNewTokenAfterRelogin() {

        user.increaseLoginVersion();

        Jwt newJwt = createJwt(
                "USER",
                user.getLoginVersion()
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(newJwt);

        assertFalse(result.hasErrors());
    }

    // ========================================
    // 10. loginVersion 클레임 누락
    // ========================================

    @Test
    void shouldRejectTokenWithoutLoginVersion() {

        Jwt tokenWithoutVersion = createJwt(
                "USER",
                null
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(tokenWithoutVersion);

        assertInvalidToken(result);
    }

    // ========================================
    // 11. loginVersion이 문자열인 경우
    // ========================================

    @Test
    void shouldRejectInvalidLoginVersionClaim() {

        Jwt invalidVersionJwt = createJwt(
                "USER",
                "invalid-version"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(invalidVersionJwt);

        assertInvalidToken(result);
    }

    // ========================================
    // 12. NORMAL JWT 검증 성공
    // ========================================

    @Test
    void shouldAcceptNormalAuthType() {

        Jwt normalJwt = createJwt(
                "USER",
                user.getLoginVersion(),
                "NORMAL"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(normalJwt);

        assertFalse(result.hasErrors());
    }

    // ========================================
    // 13. 유효한 TEMPORARY JWT 검증 성공
    // ========================================

    @Test
    void shouldAcceptValidTemporaryToken() {

        // 임시 비밀번호 만료 시간을 3분 뒤로 설정
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        Jwt temporaryJwt = createJwt(
                "USER",
                user.getLoginVersion(),
                "TEMPORARY"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(temporaryJwt);

        assertFalse(result.hasErrors());
    }

    // ========================================
    // 14. 임시 비밀번호 만료 후 JWT 인증 실패
    // ========================================

    @Test
    void shouldRejectExpiredTemporaryToken() {

        // 이미 만료된 임시 비밀번호
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().minusSeconds(1)
        );

        Jwt temporaryJwt = createJwt(
                "USER",
                user.getLoginVersion(),
                "TEMPORARY"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(temporaryJwt);

        assertInvalidToken(result);
    }

    // ========================================
    // 15. 임시 비밀번호 삭제 후 JWT 인증 실패
    // ========================================

    @Test
    void shouldRejectTemporaryTokenAfterPasswordCleared() {

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        Jwt temporaryJwt = createJwt(
                "USER",
                user.getLoginVersion(),
                "TEMPORARY"
        );

        // 비밀번호 재설정 등으로 임시 비밀번호 삭제
        user.clearTemporaryPassword();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(temporaryJwt);

        assertInvalidToken(result);
    }

    // ========================================
    // 16. 잘못된 authType JWT 인증 실패
    // ========================================

    @Test
    void shouldRejectUnknownAuthType() {

        Jwt invalidAuthTypeJwt = createJwt(
                "USER",
                user.getLoginVersion(),
                "UNKNOWN"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(invalidAuthTypeJwt);

        assertInvalidToken(result);
    }

    // ========================================
    // 17. 로그인 버전 변경 후 임시 JWT 무효화
    // ========================================

    @Test
    void shouldRejectOldTemporaryTokenAfterLoginVersionChanged() {

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        Jwt oldTemporaryJwt = createJwt(
                "USER",
                user.getLoginVersion(),
                "TEMPORARY"
        );

        // 다른 로그인 성공 등으로 버전 증가
        user.increaseLoginVersion();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        OAuth2TokenValidatorResult result =
                validator.validate(oldTemporaryJwt);

        assertInvalidToken(result);
    }

    // ========================================
    // 공통 검증 메서드
    // ========================================

    private void assertInvalidToken(
            OAuth2TokenValidatorResult result
    ) {

        assertTrue(result.hasErrors());

        assertEquals(
                "invalid_token",
                result.getErrors()
                        .iterator()
                        .next()
                        .getErrorCode()
        );
    }

    // ========================================
    // 기존 테스트용 JWT 생성 메서드
    // ========================================

    private Jwt createJwt(
            String role,
            Object loginVersion
    ) {
        return createJwt(role, loginVersion, null);
    }

    // ========================================
    // authType 포함 JWT 생성 메서드
    // ========================================

    private Jwt createJwt(
            String role,
            Object loginVersion,
            String authType
    ) {

        Instant now = Instant.now();

        Jwt.Builder builder = Jwt.withTokenValue("test.jwt.token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600));

        // 사용자 역할
        if (role != null) {
            builder.claim("role", role);
        }

        // 로그인 버전
        if (loginVersion != null) {
            builder.claim("loginVersion", loginVersion);
        }

        // 인증 유형
        if (authType != null) {
            builder.claim("authType", authType);
        }

        return builder.build();
    }
}
