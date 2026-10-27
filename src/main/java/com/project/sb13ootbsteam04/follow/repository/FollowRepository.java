
package com.project.sb13ootbsteam04.follow.repository;

import com.project.sb13ootbsteam04.follow.entity.Follow;
import com.project.sb13ootbsteam04.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    // 중복 팔로우 확인
    boolean existsByFollowerIdAndFollowingId(
            UUID followerId,
            UUID followingId
    );

    // 특정 팔로우 관계 조회
    Optional<Follow> findByFollowerIdAndFollowingId(
            UUID followerId,
            UUID followingId
    );

    // 기존 팔로잉 목록 조회
    Page<Follow> findByFollowerId(
            UUID followerId,
            Pageable pageable
    );

    // 기존 팔로워 목록 조회
    Page<Follow> findByFollowingId(
            UUID followingId,
            Pageable pageable
    );

    // 팔로잉 수
    long countByFollowerId(UUID followerId);

    // 팔로워 수
    long countByFollowingId(UUID followingId);

    // 팔로잉 첫 페이지 조회 (팔로우 대상 이름 검색)
    @Query("""
            SELECT f
            FROM Follow f, User u
            WHERE f.followerId = :followerId
              AND u.id = f.followingId
              AND (
                  :nameLike IS NULL
                  OR LOWER(u.name) LIKE LOWER(
                      CONCAT('%', :nameLike, '%')
                  )
              )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Follow> findFirstFollowingPage(
            @Param("followerId") UUID followerId,
            @Param("nameLike") String nameLike,
            Pageable pageable
    );

    // 팔로잉 다음 페이지 조회
    @Query("""
            SELECT f
            FROM Follow f, User u
            WHERE f.followerId = :followerId
              AND u.id = f.followingId
              AND (
                  :nameLike IS NULL
                  OR LOWER(u.name) LIKE LOWER(
                      CONCAT('%', :nameLike, '%')
                  )
              )
              AND (
                  f.createdAt < :cursor
                  OR (
                      f.createdAt = :cursor
                      AND f.id < :idAfter
                  )
              )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Follow> findNextFollowingPage(
            @Param("followerId") UUID followerId,
            @Param("nameLike") String nameLike,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    // 이름 검색 조건을 적용한 팔로잉 수
    @Query("""
            SELECT COUNT(f)
            FROM Follow f, User u
            WHERE f.followerId = :followerId
              AND u.id = f.followingId
              AND (
                  :nameLike IS NULL
                  OR LOWER(u.name) LIKE LOWER(
                      CONCAT('%', :nameLike, '%')
                  )
              )
            """)
    long countFollowingsByName(
            @Param("followerId") UUID followerId,
            @Param("nameLike") String nameLike
    );

    // 팔로워 첫 페이지 조회 (팔로워 이름 검색)
    @Query("""
            SELECT f
            FROM Follow f, User u
            WHERE f.followingId = :followeeId
              AND u.id = f.followerId
              AND (
                  :nameLike IS NULL
                  OR LOWER(u.name) LIKE LOWER(
                      CONCAT('%', :nameLike, '%')
                  )
              )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Follow> findFirstFollowerPage(
            @Param("followeeId") UUID followeeId,
            @Param("nameLike") String nameLike,
            Pageable pageable
    );

    // 팔로워 다음 페이지 조회
    @Query("""
            SELECT f
            FROM Follow f, User u
            WHERE f.followingId = :followeeId
              AND u.id = f.followerId
              AND (
                  :nameLike IS NULL
                  OR LOWER(u.name) LIKE LOWER(
                      CONCAT('%', :nameLike, '%')
                  )
              )
              AND (
                  f.createdAt < :cursor
                  OR (
                      f.createdAt = :cursor
                      AND f.id < :idAfter
                  )
              )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Follow> findNextFollowerPage(
            @Param("followeeId") UUID followeeId,
            @Param("nameLike") String nameLike,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    // 이름 검색 조건을 적용한 팔로워 수
    @Query("""
            SELECT COUNT(f)
            FROM Follow f, User u
            WHERE f.followingId = :followeeId
              AND u.id = f.followerId
              AND (
                  :nameLike IS NULL
                  OR LOWER(u.name) LIKE LOWER(
                      CONCAT('%', :nameLike, '%')
                  )
              )
            """)
    long countFollowersByName(
            @Param("followeeId") UUID followeeId,
            @Param("nameLike") String nameLike
    );
}
