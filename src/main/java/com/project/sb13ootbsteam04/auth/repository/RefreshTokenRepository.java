package com.project.sb13ootbsteam04.auth.repository;

import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    // 토큰 해시를 이용한 Refresh Token 조회
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 영속성 컨텍스트에 토큰 엔티티를 등록하지 않고 사용자 ID만 조회
    @Query("""
            SELECT r.user.id
            FROM RefreshToken r
            WHERE r.tokenHash = :tokenHash
            """)
    Optional<UUID> findUserIdByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    // 토큰 갱신 시 동시 요청 제어
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM RefreshToken r
            WHERE r.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    // 만료된 Refresh Token 일괄 삭제
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM RefreshToken r
            WHERE r.expiresAt <= :now
            """)
    int deleteByExpiresAtLessThanEqual(@Param("now") Instant now);
}
