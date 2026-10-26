
package com.project.sb13ootbsteam04.follow.service;

import com.project.sb13ootbsteam04.follow.dto.CreateFollowRequest;
import com.project.sb13ootbsteam04.follow.entity.Follow;
import com.project.sb13ootbsteam04.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;

    // 팔로우 등록
    @Transactional
    public UUID createFollow(CreateFollowRequest request) {

        UUID followerId = request.followerId();
        UUID followeeId = request.followeeId();

        // 필수값 확인
        if (followerId == null || followeeId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        // 자기 자신 팔로우 방지
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("자기 자신을 팔로우할 수 없습니다.");
        }

        // 중복 팔로우 방지
        if (followRepository.existsByFollowerIdAndFollowingId(
                followerId, followeeId)) {
            throw new IllegalStateException("이미 팔로우한 사용자입니다.");
        }

        Follow follow = new Follow(followerId, followeeId);
        Follow savedFollow = followRepository.save(follow);

        return savedFollow.getId();
    }

    // 팔로우 취소
    @Transactional
    public void deleteFollow(UUID followId) {

        Follow follow = followRepository.findById(followId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 팔로우 관계입니다."));

        followRepository.delete(follow);
    }

    // 특정 사용자의 팔로잉 목록 조회
    public Page<Follow> getFollowings(UUID userId, Pageable pageable) {

        return followRepository.findByFollowerId(userId, pageable);
    }

    // 특정 사용자의 팔로워 목록 조회
    public Page<Follow> getFollowers(UUID userId, Pageable pageable) {

        return followRepository.findByFollowingId(userId, pageable);
    }

    // 특정 사용자의 팔로워 수
    public long getFollowerCount(UUID userId) {

        return followRepository.countByFollowingId(userId);
    }

    // 특정 사용자의 팔로잉 수
    public long getFollowingCount(UUID userId) {

        return followRepository.countByFollowerId(userId);
    }

    // 특정 사용자를 팔로우하고 있는지 확인
    public boolean isFollowing(UUID followerId, UUID followeeId) {

        return followRepository.existsByFollowerIdAndFollowingId(
                followerId, followeeId
        );
    }
}
