
package com.project.sb13ootbsteam04.follow.service;

import com.project.sb13ootbsteam04.follow.dto.CreateFollowRequest;
import com.project.sb13ootbsteam04.follow.dto.FollowPageResponse;
import com.project.sb13ootbsteam04.follow.dto.FollowResponse;
import com.project.sb13ootbsteam04.follow.dto.FollowSummaryResponse;
import com.project.sb13ootbsteam04.follow.entity.Follow;
import com.project.sb13ootbsteam04.follow.repository.FollowRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final FollowResponseService followResponseService;

    // 팔로우 생성
    @Transactional
    public UUID createFollow(CreateFollowRequest request) {

        validateCreateRequest(request);

        Follow follow = new Follow(
                request.followerId(),
                request.followeeId()
        );

        Follow savedFollow = followRepository.saveAndFlush(follow);

        return savedFollow.getId();
    }

    // 팔로우 생성 후 응답 DTO 반환
    @Transactional
    public FollowResponse createFollowResponse(CreateFollowRequest request) {

        UUID followId = createFollow(request);

        Follow follow = followRepository.findById(followId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "생성된 팔로우 정보를 찾을 수 없습니다: " + followId
                        ));

        return followResponseService.toResponse(follow);
    }

    // 팔로우 취소
    @Transactional
    public void deleteFollow(UUID followId) {

        if (followId == null) {
            throw new IllegalArgumentException("팔로우 ID는 필수입니다.");
        }

        Follow follow = followRepository.findById(followId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 팔로우입니다: " + followId
                        ));

        followRepository.delete(follow);
    }

    // 기존 팔로잉 목록 조회
    public Page<Follow> getFollowings(
            UUID followerId,
            Pageable pageable
    ) {

        validateUserExists(followerId);

        return followRepository.findByFollowerId(
                followerId,
                pageable
        );
    }

    // 기존 팔로워 목록 조회
    public Page<Follow> getFollowers(
            UUID followingId,
            Pageable pageable
    ) {

        validateUserExists(followingId);

        return followRepository.findByFollowingId(
                followingId,
                pageable
        );
    }

    // 팔로워 수 조회
    public long getFollowerCount(UUID userId) {

        validateUserExists(userId);

        return followRepository.countByFollowingId(userId);
    }

    // 팔로잉 수 조회
    public long getFollowingCount(UUID userId) {

        validateUserExists(userId);

        return followRepository.countByFollowerId(userId);
    }

    // 팔로우 여부 확인
    public boolean isFollowing(
            UUID followerId,
            UUID followingId
    ) {

        if (followerId == null || followingId == null) {
            return false;
        }

        return followRepository.existsByFollowerIdAndFollowingId(
                followerId,
                followingId
        );
    }

    // 팔로우 요약 정보 조회
    public FollowSummaryResponse getFollowSummary(
            UUID userId,
            UUID currentUserId
    ) {

        validateUserExists(userId);

        long followerCount =
                followRepository.countByFollowingId(userId);

        long followingCount =
                followRepository.countByFollowerId(userId);

        boolean followedByMe = false;
        UUID followedByMeId = null;
        boolean followingMe = false;

        if (currentUserId != null) {

            validateUserExists(currentUserId);

            var myFollow =
                    followRepository.findByFollowerIdAndFollowingId(
                            currentUserId,
                            userId
                    );

            followedByMe = myFollow.isPresent();

            if (myFollow.isPresent()) {
                followedByMeId = myFollow.get().getId();
            }

            followingMe =
                    followRepository.existsByFollowerIdAndFollowingId(
                            userId,
                            currentUserId
                    );
        }

        return new FollowSummaryResponse(
                userId,
                followerCount,
                followingCount,
                followedByMe,
                followedByMeId,
                followingMe
        );
    }

    // 팔로잉 목록 커서 페이지네이션
    public FollowPageResponse getFollowingPage(
            UUID followerId,
            String cursor,
            UUID idAfter,
            int limit,
            String nameLike
    ) {

        validateUserExists(followerId);
        validatePageRequest(cursor, idAfter, limit);

        String normalizedNameLike = normalizeNameLike(nameLike);
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<Follow> follows;

        if (cursor == null || cursor.isBlank()) {

            follows = followRepository.findFirstFollowingPage(
                    followerId,
                    normalizedNameLike,
                    pageable
            );

        } else {

            Instant cursorTime = parseCursor(cursor);

            follows = followRepository.findNextFollowingPage(
                    followerId,
                    normalizedNameLike,
                    cursorTime,
                    idAfter,
                    pageable
            );
        }

        long totalCount = followRepository.countFollowingsByName(
                followerId,
                normalizedNameLike
        );

        return toPageResponse(follows, limit, totalCount);
    }

    // 팔로워 목록 커서 페이지네이션
    public FollowPageResponse getFollowerPage(
            UUID followeeId,
            String cursor,
            UUID idAfter,
            int limit,
            String nameLike
    ) {

        validateUserExists(followeeId);
        validatePageRequest(cursor, idAfter, limit);

        String normalizedNameLike = normalizeNameLike(nameLike);
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<Follow> follows;

        if (cursor == null || cursor.isBlank()) {

            // 첫 페이지: 나를 팔로우하는 사용자 조회
            follows = followRepository.findFirstFollowerPage(
                    followeeId,
                    normalizedNameLike,
                    pageable
            );

        } else {

            // 다음 페이지: 커서 기준 조회
            Instant cursorTime = parseCursor(cursor);

            follows = followRepository.findNextFollowerPage(
                    followeeId,
                    normalizedNameLike,
                    cursorTime,
                    idAfter,
                    pageable
            );
        }

        // 검색 조건을 적용한 전체 팔로워 수
        long totalCount = followRepository.countFollowersByName(
                followeeId,
                normalizedNameLike
        );

        return toPageResponse(follows, limit, totalCount);
    }

    // 팔로잉 및 팔로워 목록 공통 응답 생성
    private FollowPageResponse toPageResponse(
            List<Follow> follows,
            int limit,
            long totalCount
    ) {

        boolean hasNext = follows.size() > limit;

        List<Follow> pageFollows = follows.stream()
                .limit(limit)
                .toList();

        List<FollowResponse> data = pageFollows.stream()
                .map(followResponseService::toResponse)
                .toList();

        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !pageFollows.isEmpty()) {

            Follow last = pageFollows.get(pageFollows.size() - 1);

            nextCursor = last.getCreatedAt().toString();
            nextIdAfter = last.getId();
        }

        return new FollowPageResponse(
                data,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                "createdAt",
                "DESCENDING"
        );
    }

    // 페이지 요청값 검증
    private void validatePageRequest(
            String cursor,
            UUID idAfter,
            int limit
    ) {

        if (limit < 1 || limit == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "limit은 1 이상이고 너무 큰 값은 사용할 수 없습니다."
            );
        }

        boolean hasCursor = cursor != null && !cursor.isBlank();

        if (hasCursor != (idAfter != null)) {
            throw new IllegalArgumentException(
                    "cursor와 idAfter는 함께 전달해야 합니다."
            );
        }
    }

    // 커서 문자열을 Instant로 변환
    private Instant parseCursor(String cursor) {

        try {
            return Instant.parse(cursor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "cursor는 ISO-8601 날짜 형식이어야 합니다.",
                    e
            );
        }
    }

    // 이름 검색 조건 정리
    private String normalizeNameLike(String nameLike) {

        if (nameLike == null || nameLike.isBlank()) {
            return null;
        }

        return nameLike.trim();
    }

    // 팔로우 등록 요청 검증
    private void validateCreateRequest(CreateFollowRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "팔로우 등록 요청은 필수입니다."
            );
        }

        UUID followerId = request.followerId();
        UUID followeeId = request.followeeId();

        if (followerId == null || followeeId == null) {
            throw new IllegalArgumentException(
                    "팔로워 ID와 팔로우 대상 ID는 필수입니다."
            );
        }

        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException(
                    "자기 자신을 팔로우할 수 없습니다."
            );
        }

        validateUserExists(followerId);
        validateUserExists(followeeId);

        if (followRepository.existsByFollowerIdAndFollowingId(
                followerId,
                followeeId
        )) {
            throw new IllegalArgumentException(
                    "이미 팔로우한 사용자입니다."
            );
        }
    }

    // 사용자 존재 여부 검증
    private void validateUserExists(UUID userId) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "사용자 ID는 필수입니다."
            );
        }

        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException(
                    "존재하지 않는 사용자입니다: " + userId
            );
        }
    }
}
