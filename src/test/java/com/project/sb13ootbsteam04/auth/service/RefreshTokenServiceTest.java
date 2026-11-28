package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
import com.project.sb13ootbsteam04.auth.entity.RefreshTokenRevocationReason;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000001"
            );

    private static final String RAW_TOKEN =
            "a".repeat(43);

    private static final String TOKEN_HASH =
            "a".repeat(64);

    private static final String NEW_RAW_TOKEN =
            "b".repeat(43);

    private static final String NEW_TOKEN_HASH =
            "b".repeat(64);

    private static final String INVALID_TOKEN_MESSAGE =
            "유효하지 않은 Refresh Token입니다.";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {

        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedPassword"
        );

        ReflectionTestUtils.setField(
                user,
                "id",
                USER_ID
        );

        Instant now = Instant.now();

        refreshToken = new RefreshToken(
                user,
                TOKEN_HASH,
                now.minusSeconds(60),
                now.plusSeconds(3600)
        );
    }

    // ========================================
    // Refresh Token 발급 테스트
    // ========================================

    // 1. 신규 Refresh Token 정상 발급
    @Test
    void shouldIssueRefreshToken() {

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        when(refreshTokenGenerator.generate())
                .thenReturn(RAW_TOKEN);

        when(refreshTokenGenerator.hash(RAW_TOKEN))
                .thenReturn(TOKEN_HASH);

        RefreshTokenService.IssuedRefreshToken result =
                refreshTokenService.issue(user);

        assertEquals(USER_ID, result.userId());
        assertEquals(RAW_TOKEN, result.rawToken());
        assertNotNull(result.expiresAt());

        ArgumentCaptor<RefreshToken> captor =
                ArgumentCaptor.forClass(RefreshToken.class);

        verify(refreshTokenRepository)
                .save(captor.capture());

        RefreshToken savedToken = captor.getValue();

        assertEquals(user, savedToken.getUser());
        assertEquals(TOKEN_HASH, savedToken.getTokenHash());
        assertNotEquals(
                RAW_TOKEN,
                savedToken.getTokenHash()
        );

        assertEquals(
                user.getLoginVersion(),
                savedToken.getLoginVersion()
        );

        assertEquals(
                result.expiresAt(),
                savedToken.getExpiresAt()
        );

        assertEquals(
                Duration.ofDays(7),
                Duration.between(
                        savedToken.getCreatedAt(),
                        savedToken.getExpiresAt()
                )
        );

        assertFalse(savedToken.isRevoked());

        verifyNoInteractions(jwtTokenProvider);
    }

    // 2. 잠긴 계정은 Refresh Token 발급 거부
    @Test
    void shouldRejectIssueWhenUserIsLocked() {

        user.changeLocked(true);

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        assertInvalidToken(
                () -> refreshTokenService.issue(user)
        );

        verifyNoInteractions(
                refreshTokenRepository,
                refreshTokenGenerator,
                jwtTokenProvider
        );
    }

    // 3. 존재하지 않는 사용자에게 토큰 발급 거부
    @Test
    void shouldRejectIssueWhenUserDoesNotExist() {

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.empty());

        assertInvalidToken(
                () -> refreshTokenService.issue(user)
        );

        verifyNoInteractions(
                refreshTokenRepository,
                refreshTokenGenerator,
                jwtTokenProvider
        );
    }

    // ========================================
    // Refresh Token Rotation 테스트
    // ========================================

    // 4. 정상 토큰 갱신 및 Access Token 재발급
    @Test
    void shouldRotateRefreshTokenSuccessfully() {

        mockTokenLookup(refreshToken);

        when(refreshTokenGenerator.generate())
                .thenReturn(NEW_RAW_TOKEN);

        when(refreshTokenGenerator.hash(NEW_RAW_TOKEN))
                .thenReturn(NEW_TOKEN_HASH);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("new.jwt.token");

        long previousVersion = user.getLoginVersion();

        LoginResult result =
                refreshTokenService.rotate(RAW_TOKEN);

        assertTrue(refreshToken.isRevoked());
        assertNotNull(refreshToken.getRevokedAt());

        assertEquals(
                "new.jwt.token",
                result.response().accessToken()
        );

        assertEquals(
                "Bearer",
                result.response().tokenType()
        );

        assertEquals(
                USER_ID,
                result.response().userDto().id()
        );

        assertEquals(
                "test@example.com",
                result.response().userDto().email()
        );

        assertTrue(result.hasRefreshToken());

        assertEquals(
                NEW_RAW_TOKEN,
                result.refreshToken().rawToken()
        );

        assertEquals(
                previousVersion,
                user.getLoginVersion()
        );

        ArgumentCaptor<RefreshToken> captor =
                ArgumentCaptor.forClass(RefreshToken.class);

        verify(refreshTokenRepository)
                .save(captor.capture());

        RefreshToken newToken = captor.getValue();

        assertEquals(
                NEW_TOKEN_HASH,
                newToken.getTokenHash()
        );

        assertEquals(
                previousVersion,
                newToken.getLoginVersion()
        );

        assertFalse(newToken.isRevoked());

        assertEquals(
                refreshToken.getExpiresAt(),
                newToken.getExpiresAt()
        );

        assertEquals(
                refreshToken.getExpiresAt(),
                result.refreshToken().expiresAt()
        );

        verify(jwtTokenProvider)
                .generateAccessToken(user);
    }

    // 5. 갱신 시 사용자 → 토큰 순서로 잠금 획득
    @Test
    void shouldAcquireLocksInCorrectOrder() {

        mockTokenLookup(refreshToken);

        when(refreshTokenGenerator.generate())
                .thenReturn(NEW_RAW_TOKEN);

        when(refreshTokenGenerator.hash(NEW_RAW_TOKEN))
                .thenReturn(NEW_TOKEN_HASH);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("new.jwt.token");

        refreshTokenService.rotate(RAW_TOKEN);

        InOrder inOrder = inOrder(
                refreshTokenGenerator,
                refreshTokenRepository,
                userRepository,
                jwtTokenProvider
        );

        inOrder.verify(refreshTokenGenerator)
                .hash(RAW_TOKEN);

        inOrder.verify(refreshTokenRepository)
                .findUserIdByTokenHash(TOKEN_HASH);

        inOrder.verify(userRepository)
                .findByIdForUpdate(USER_ID);

        inOrder.verify(refreshTokenRepository)
                .findByTokenHashForUpdate(TOKEN_HASH);

        inOrder.verify(refreshTokenGenerator)
                .generate();

        inOrder.verify(refreshTokenGenerator)
                .hash(NEW_RAW_TOKEN);

        inOrder.verify(refreshTokenRepository)
                .save(any(RefreshToken.class));

        inOrder.verify(jwtTokenProvider)
                .generateAccessToken(user);
    }

    // 6. 만료된 Refresh Token 갱신 거부
    @Test
    void shouldRejectExpiredRefreshToken() {

        Instant now = Instant.now();

        RefreshToken expiredToken = new RefreshToken(
                user,
                TOKEN_HASH,
                now.minusSeconds(3600),
                now.minusSeconds(60)
        );

        mockTokenLookup(expiredToken);

        assertInvalidToken(
                () -> refreshTokenService.rotate(RAW_TOKEN)
        );

        assertFalse(expiredToken.isRevoked());

        verifyNoNewTokenIssued();
    }

    // 7. 이미 폐기된 Refresh Token 재사용 거부
    @Test
    void shouldRejectRevokedRefreshToken() {

        refreshToken.revoke(Instant.now());

        mockTokenLookup(refreshToken);

        assertInvalidToken(
                () -> refreshTokenService.rotate(RAW_TOKEN)
        );

        verifyNoNewTokenIssued();
    }

    // 8. 잠긴 계정의 토큰 갱신 거부
    @Test
    void shouldRejectRefreshWhenUserIsLocked() {

        user.changeLocked(true);

        mockTokenLookup(refreshToken);

        assertInvalidToken(
                () -> refreshTokenService.rotate(RAW_TOKEN)
        );

        verifyNoNewTokenIssued();
    }

    // 9. loginVersion 불일치 시 갱신 거부
    @Test
    void shouldRejectRefreshWhenLoginVersionChanges() {

        user.increaseLoginVersion();

        mockTokenLookup(refreshToken);

        assertInvalidToken(
                () -> refreshTokenService.rotate(RAW_TOKEN)
        );

        verifyNoNewTokenIssued();
    }

    // 10. 존재하지 않는 Refresh Token 거부
    @Test
    void shouldRejectUnknownRefreshToken() {

        when(refreshTokenGenerator.hash(RAW_TOKEN))
                .thenReturn(TOKEN_HASH);

        when(refreshTokenRepository
                .findUserIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.empty());

        assertInvalidToken(
                () -> refreshTokenService.rotate(RAW_TOKEN)
        );

        verifyNoInteractions(userRepository);

        verifyNoNewTokenIssued();
    }

    // 11. 토큰 소유자가 삭제된 경우 갱신 거부
    @Test
    void shouldRejectRefreshWhenUserDoesNotExist() {

        when(refreshTokenGenerator.hash(RAW_TOKEN))
                .thenReturn(TOKEN_HASH);

        when(refreshTokenRepository
                .findUserIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(USER_ID));

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.empty());

        assertInvalidToken(
                () -> refreshTokenService.rotate(RAW_TOKEN)
        );

        verify(refreshTokenRepository, never())
                .findByTokenHashForUpdate(any());

        verifyNoNewTokenIssued();
    }

    // 12. 잠금 획득 전 토큰이 삭제된 경우 갱신 거부
    @Test
    void shouldRejectRefreshWhenTokenDisappears() {

        when(refreshTokenGenerator.hash(RAW_TOKEN))
                .thenReturn(TOKEN_HASH);

        when(refreshTokenRepository
                .findUserIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(USER_ID));

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        when(refreshTokenRepository
                .findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.empty());

        assertInvalidToken(
                () -> refreshTokenService.rotate(RAW_TOKEN)
        );

        verifyNoNewTokenIssued();
    }

    // ========================================
    // 잘못된 요청 형식 검증
    // ========================================

    // 13. null 토큰 거부
    @Test
    void shouldRejectNullRefreshToken() {

        assertInvalidToken(
                () -> refreshTokenService.rotate(null)
        );

        verifyNoTokenLookup();
    }

    // 14. 빈 문자열 토큰 거부
    @Test
    void shouldRejectEmptyRefreshToken() {

        assertInvalidToken(
                () -> refreshTokenService.rotate("")
        );

        verifyNoTokenLookup();
    }

    // 15. 잘못된 길이의 토큰 거부
    @Test
    void shouldRejectInvalidTokenLength() {

        assertInvalidToken(
                () -> refreshTokenService.rotate(
                        "a".repeat(42)
                )
        );

        verifyNoTokenLookup();
    }

    // 16. 허용되지 않는 문자가 포함된 토큰 거부
    @Test
    void shouldRejectInvalidTokenCharacters() {

        assertInvalidToken(
                () -> refreshTokenService.rotate(
                        "a".repeat(42) + "+"
                )
        );

        verifyNoTokenLookup();
    }

    // ========================================
    // Refresh Token 폐기 테스트
    // ========================================

    // 17. Refresh Token 정상 폐기
    @Test
    void shouldRevokeRefreshToken() {

        mockTokenLookup(refreshToken);

        refreshTokenService.revoke(RAW_TOKEN);

        assertTrue(refreshToken.isRevoked());
        assertNotNull(refreshToken.getRevokedAt());

        verifyNoNewTokenIssued();
    }

    // 18. 중복 폐기 시 최초 폐기 시간 유지
    @Test
    void shouldPreserveFirstRevocationTime() {

        mockTokenLookup(refreshToken);

        refreshTokenService.revoke(RAW_TOKEN);

        Instant firstRevokedAt =
                refreshToken.getRevokedAt();

        refreshTokenService.revoke(RAW_TOKEN);

        assertEquals(
                firstRevokedAt,
                refreshToken.getRevokedAt()
        );

        verify(refreshTokenRepository, times(2))
                .findByTokenHashForUpdate(TOKEN_HASH);

        verifyNoNewTokenIssued();
    }

    // 19. 존재하지 않는 토큰의 폐기 요청 거부
    @Test
    void shouldRejectRevokeWhenTokenDoesNotExist() {

        when(refreshTokenGenerator.hash(RAW_TOKEN))
                .thenReturn(TOKEN_HASH);

        when(refreshTokenRepository
                .findUserIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.empty());

        assertInvalidToken(
                () -> refreshTokenService.revoke(RAW_TOKEN)
        );

        verifyNoInteractions(userRepository);

        verifyNoNewTokenIssued();
    }

    // 20. 잘못된 토큰의 폐기 요청 거부
    @Test
    void shouldRejectRevokeWhenTokenFormatIsInvalid() {

        assertInvalidToken(
                () -> refreshTokenService.revoke(null)
        );

        verifyNoTokenLookup();
    }

    // ========================================
    // 내부 전달 객체 보안 테스트
    // ========================================

    // 21. toString 호출 시 토큰 원문 노출 방지
    @Test
    void shouldNotExposeRawTokenInToString() {

        RefreshTokenService.IssuedRefreshToken issuedToken =
                new RefreshTokenService.IssuedRefreshToken(
                        USER_ID,
                        RAW_TOKEN,
                        Instant.now().plusSeconds(3600)
                );

        assertFalse(
                issuedToken.toString().contains(RAW_TOKEN)
        );

        assertEquals(
                "IssuedRefreshToken[REDACTED]",
                issuedToken.toString()
        );
    }

    // ========================================
    // 로그아웃 테스트
    // ========================================

    // 22. 정상 로그아웃 시 토큰 폐기
    @Test
    void shouldRevokeTokenOnLogout() {
        mockTokenLookup(refreshToken);
        refreshTokenService.logout(RAW_TOKEN);
        assertTrue(refreshToken.isRevoked());
        assertNotNull(refreshToken.getRevokedAt());
        verifyNoNewTokenIssued();
    }

    // 23. 정상 로그아웃 시 loginVersion 증가
    @Test
    void shouldIncreaseLoginVersionOnLogout() {
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(previousVersion + 1, user.getLoginVersion());
        assertFalse(refreshToken.isValid(Instant.now()));
        verifyNoNewTokenIssued();
    }

    // 24. 쿠키 누락
    @Test
    void shouldIgnoreNullTokenOnLogout() {
        long previousVersion = user.getLoginVersion();
        assertDoesNotThrow(() -> refreshTokenService.logout(null));
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoTokenLookup();
    }

    // 25. 빈 토큰
    @Test
    void shouldIgnoreEmptyTokenOnLogout() {
        long previousVersion = user.getLoginVersion();
        assertDoesNotThrow(() -> refreshTokenService.logout(""));
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoTokenLookup();
    }

    // 26. 잘못된 형식
    @Test
    void shouldIgnoreInvalidTokenFormatOnLogout() {
        long previousVersion = user.getLoginVersion();
        assertDoesNotThrow(() -> refreshTokenService.logout("a".repeat(42)));
        assertDoesNotThrow(() -> refreshTokenService.logout("a".repeat(42) + "+"));
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoTokenLookup();
    }

    // 27. 존재하지 않는 토큰
    @Test
    void shouldIgnoreUnknownTokenOnLogout() {
        when(refreshTokenGenerator.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findUserIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.empty());
        long previousVersion = user.getLoginVersion();
        assertDoesNotThrow(() -> refreshTokenService.logout(RAW_TOKEN));
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoInteractions(userRepository);
        verify(refreshTokenRepository, never()).findByTokenHashForUpdate(any());
        verifyNoNewTokenIssued();
    }

    // 28. 삭제된 사용자
    @Test
    void shouldIgnoreDeletedUserOnLogout() {
        when(refreshTokenGenerator.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findUserIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());
        long previousVersion = user.getLoginVersion();
        assertDoesNotThrow(() -> refreshTokenService.logout(RAW_TOKEN));
        assertEquals(previousVersion, user.getLoginVersion());
        verify(refreshTokenRepository, never()).findByTokenHashForUpdate(any());
        verifyNoNewTokenIssued();
    }

    // 29. 잠금 획득 과정에서 삭제된 토큰
    @Test
    void shouldIgnoreDisappearedTokenOnLogout() {
        when(refreshTokenGenerator.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findUserIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.empty());
        long previousVersion = user.getLoginVersion();
        assertDoesNotThrow(() -> refreshTokenService.logout(RAW_TOKEN));
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoNewTokenIssued();
    }

    // 30. 이미 폐기된 토큰
    @Test
    void shouldIgnoreRevokedTokenOnLogout() {
        refreshToken.revoke(Instant.now());
        Instant revokedAt = refreshToken.getRevokedAt();
        long previousVersion = user.getLoginVersion();
        mockTokenLookup(refreshToken);
        assertDoesNotThrow(() -> refreshTokenService.logout(RAW_TOKEN));
        assertEquals(revokedAt, refreshToken.getRevokedAt());
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoNewTokenIssued();
    }

    // 31. 만료된 토큰
    @Test
    void shouldIgnoreExpiredTokenOnLogout() {
        Instant now = Instant.now();
        RefreshToken expiredToken = new RefreshToken(user, TOKEN_HASH,
                now.minusSeconds(3600), now.minusSeconds(60));
        mockTokenLookup(expiredToken);
        long previousVersion = user.getLoginVersion();
        assertDoesNotThrow(() -> refreshTokenService.logout(RAW_TOKEN));
        assertEquals(previousVersion, user.getLoginVersion());
        assertFalse(expiredToken.isRevoked());
        verifyNoNewTokenIssued();
    }

    // 32. 잠긴 사용자
    @Test
    void shouldIgnoreLockedUserOnLogout() {
        user.changeLocked(true);
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        assertDoesNotThrow(() -> refreshTokenService.logout(RAW_TOKEN));
        assertEquals(previousVersion, user.getLoginVersion());
        assertFalse(refreshToken.isRevoked());
        verifyNoNewTokenIssued();
    }

    // 33. 이전 로그인 토큰이 현재 세션을 무효화하지 않음
    @Test
    void shouldPreserveCurrentSessionWhenOldTokenLogsOut() {
        user.increaseLoginVersion();
        Instant now = Instant.now();
        RefreshToken currentToken = new RefreshToken(user, NEW_TOKEN_HASH,
                now.minusSeconds(1), now.plusSeconds(3600));
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        assertDoesNotThrow(() -> refreshTokenService.logout(RAW_TOKEN));
        assertEquals(previousVersion, user.getLoginVersion());
        assertFalse(refreshToken.isRevoked());
        assertTrue(currentToken.isValid(Instant.now()));
        verifyNoNewTokenIssued();
    }

    // 34. 반복 로그아웃 시 최초 폐기 시간 및 버전 유지
    @Test
    void shouldNotIncreaseVersionTwiceOnRepeatedLogout() {
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        Instant firstRevokedAt = refreshToken.getRevokedAt();
        assertNotNull(firstRevokedAt);
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(previousVersion + 1, user.getLoginVersion());
        assertEquals(firstRevokedAt, refreshToken.getRevokedAt());
        verify(refreshTokenRepository, times(2)).findByTokenHashForUpdate(TOKEN_HASH);
        verifyNoNewTokenIssued();
    }

    // 35. 사용자 -> 토큰 잠금 순서
    @Test
    void shouldAcquireLocksInCorrectOrderOnLogout() {
        mockTokenLookup(refreshToken);
        refreshTokenService.logout(RAW_TOKEN);
        InOrder order = inOrder(refreshTokenGenerator, refreshTokenRepository, userRepository);
        order.verify(refreshTokenGenerator).hash(RAW_TOKEN);
        order.verify(refreshTokenRepository).findUserIdByTokenHash(TOKEN_HASH);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(refreshTokenRepository).findByTokenHashForUpdate(TOKEN_HASH);
        assertTrue(refreshToken.isRevoked());
        verifyNoNewTokenIssued();
    }

    // 36. DB 장애 예외 전파 (실제 롤백은 통합 테스트에서 검증)
    @Test
    void shouldPropagateDatabaseFailureOnLogout() {
        when(refreshTokenGenerator.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findUserIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        var failure = new org.springframework.dao.DataAccessResourceFailureException("DB failure");
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenThrow(failure);
        long previousVersion = user.getLoginVersion();
        assertSame(failure, assertThrows(
                org.springframework.dao.DataAccessResourceFailureException.class,
                () -> refreshTokenService.logout(RAW_TOKEN)));
        assertEquals(previousVersion, user.getLoginVersion());
        assertFalse(refreshToken.isRevoked());
        verifyNoNewTokenIssued();
    }


    // Rotation 및 로그아웃 유예 정책 테스트
    @Test
    void shouldRecordRotationReason() {
        mockTokenLookup(refreshToken);
        when(refreshTokenGenerator.generate()).thenReturn(NEW_RAW_TOKEN);
        when(refreshTokenGenerator.hash(NEW_RAW_TOKEN)).thenReturn(NEW_TOKEN_HASH);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("new.jwt.token");
        refreshTokenService.rotate(RAW_TOKEN);
        assertEquals(RefreshTokenRevocationReason.ROTATED, refreshToken.getRevocationReason());
    }

    @Test
    void shouldRecordGeneralRevocationReason() {
        mockTokenLookup(refreshToken);
        refreshTokenService.revoke(RAW_TOKEN);
        assertEquals(RefreshTokenRevocationReason.REVOKED, refreshToken.getRevocationReason());
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldRecordLogoutReason() {
        mockTokenLookup(refreshToken);
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(RefreshTokenRevocationReason.LOGOUT, refreshToken.getRevocationReason());
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldLogoutRecentlyRotatedToken() {
        refreshToken.revoke(Instant.now(), RefreshTokenRevocationReason.ROTATED);
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(previousVersion + 1, user.getLoginVersion());
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldPreserveOriginalRotationInformationOnLogout() {
        Instant revokedAt = Instant.now();
        refreshToken.revoke(revokedAt, RefreshTokenRevocationReason.ROTATED);
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(previousVersion + 1, user.getLoginVersion());
        assertEquals(revokedAt, refreshToken.getRevokedAt());
        assertEquals(RefreshTokenRevocationReason.ROTATED, refreshToken.getRevocationReason());
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldIgnoreRotationOutsideGracePeriod() {
        refreshToken.revoke(Instant.now().minusSeconds(10), RefreshTokenRevocationReason.ROTATED);
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldIgnoreRotationFromPreviousLogin() {
        refreshToken.revoke(Instant.now(), RefreshTokenRevocationReason.ROTATED);
        user.increaseLoginVersion();
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldIgnoreExpiredRotatedToken() {
        Instant now = Instant.now();
        RefreshToken expired = new RefreshToken(user, TOKEN_HASH,
                now.minusSeconds(60), now.minusSeconds(1));
        expired.revoke(now.minusSeconds(2), RefreshTokenRevocationReason.ROTATED);
        mockTokenLookup(expired);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldIgnoreRotatedTokenForLockedAccount() {
        refreshToken.revoke(Instant.now(), RefreshTokenRevocationReason.ROTATED);
        user.changeLocked(true);
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldIncreaseVersionOnlyOnceForRotatedTokenLogout() {
        refreshToken.revoke(Instant.now(), RefreshTokenRevocationReason.ROTATED);
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        refreshTokenService.logout(RAW_TOKEN);
        assertEquals(previousVersion + 1, user.getLoginVersion());
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldRejectRotationReplayEvenWithinLogoutGracePeriod() {
        refreshToken.revoke(Instant.now(), RefreshTokenRevocationReason.ROTATED);
        mockTokenLookup(refreshToken);
        assertInvalidToken(() -> refreshTokenService.rotate(RAW_TOKEN));
        verifyNoNewTokenIssued();
    }

    @Test
    void shouldAcquireLocksInOrderForRotatedTokenLogout() {
        refreshToken.revoke(Instant.now(), RefreshTokenRevocationReason.ROTATED);
        mockTokenLookup(refreshToken);
        long previousVersion = user.getLoginVersion();
        refreshTokenService.logout(RAW_TOKEN);
        InOrder order = inOrder(refreshTokenGenerator, refreshTokenRepository, userRepository);
        order.verify(refreshTokenGenerator).hash(RAW_TOKEN);
        order.verify(refreshTokenRepository).findUserIdByTokenHash(TOKEN_HASH);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(refreshTokenRepository).findByTokenHashForUpdate(TOKEN_HASH);
        assertEquals(previousVersion + 1, user.getLoginVersion());
        verifyNoNewTokenIssued();
    }

    // ========================================
    // 공통 테스트 헬퍼
    // ========================================

    private void mockTokenLookup(RefreshToken token) {

        when(refreshTokenGenerator.hash(RAW_TOKEN))
                .thenReturn(TOKEN_HASH);

        when(refreshTokenRepository
                .findUserIdByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(USER_ID));

        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        when(refreshTokenRepository
                .findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(token));
    }

    private void verifyNoNewTokenIssued() {

        verify(refreshTokenGenerator, never())
                .generate();

        verify(refreshTokenRepository, never())
                .save(any(RefreshToken.class));

        verifyNoInteractions(jwtTokenProvider);
    }

    private void verifyNoTokenLookup() {

        verifyNoInteractions(
                refreshTokenGenerator,
                refreshTokenRepository,
                userRepository,
                jwtTokenProvider
        );
    }

    private void assertInvalidToken(Runnable action) {

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                action::run
        );

        assertEquals(
                INVALID_TOKEN_MESSAGE,
                exception.getMessage()
        );
    }
}