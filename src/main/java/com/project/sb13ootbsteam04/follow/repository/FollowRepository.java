package com.project.sb13ootbsteam04.follow.repository;

import com.project.sb13ootbsteam04.follow.entity.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    // 특정 사용자가 다른 사용자를 팔로우하고 있는지 확인
    boolean existsByFollowerIdAndFollowingId(
            UUID followerId,
            UUID followingId
    );

    // 특정 사용자 간 팔로우 관계 조회
    Optional<Follow> findByFollowerIdAndFollowingId(
            UUID followerId,
            UUID followingId
    );

    // 특정 사용자가 팔로우하는 사람 목록
    Page<Follow> findByFollowerId(
            UUID followerId,
            Pageable pageable
    );

    // 특정 사용자를 팔로우하는 사람 목록
    Page<Follow> findByFollowingId(
            UUID followingId,
            Pageable pageable
    );

    // 팔로잉 수 조회
    long countByFollowerId(UUID followerId);

    // 팔로워 수 조회
    long countByFollowingId(UUID followingId);
}