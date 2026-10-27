
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.entity.FeedLike;
import com.project.sb13ootbsteam04.feed.repository.FeedLikeRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedLikeService {

    private final FeedRepository feedRepository;
    private final FeedLikeRepository feedLikeRepository;

    // 좋아요 등록
    @Transactional
    public void likeFeed(UUID feedId, UUID userId) {

        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 피드입니다."));

        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        if (feedLikeRepository.existsByFeed_IdAndUserId(feedId, userId)) {
            throw new IllegalStateException("이미 좋아요를 누른 피드입니다.");
        }

        FeedLike feedLike = new FeedLike(feed, userId);
        feedLikeRepository.save(feedLike);
    }

    // 좋아요 취소
    @Transactional
    public void unlikeFeed(UUID feedId, UUID userId) {

        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }

        FeedLike feedLike = feedLikeRepository
                .findByFeed_IdAndUserId(feedId, userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("좋아요 기록이 존재하지 않습니다."));

        feedLikeRepository.delete(feedLike);
    }

    // 좋아요 개수 조회
    public long getLikeCount(UUID feedId) {
        return feedLikeRepository.countByFeed_Id(feedId);
    }

    // 내가 좋아요를 눌렀는지 확인
    public boolean isLikedByMe(UUID feedId, UUID userId) {
        if (userId == null) {
            return false;
        }

        return feedLikeRepository.existsByFeed_IdAndUserId(feedId, userId);
    }
}
