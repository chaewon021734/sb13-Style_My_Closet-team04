
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.dto.CreateFeedRequest;
import com.project.sb13ootbsteam04.feed.dto.UpdateFeedRequest;
import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.entity.FeedClothes;
import com.project.sb13ootbsteam04.feed.repository.CommentRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedClothesRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedLikeRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    private final FeedRepository feedRepository;
    private final FeedClothesRepository feedClothesRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final CommentRepository commentRepository;

    // 피드 등록
    @Transactional
    public UUID createFeed(CreateFeedRequest request) {

        Feed feed = new Feed(
                request.authorId(),
                request.weatherId(),
                request.content()
        );

        Feed savedFeed = feedRepository.save(feed);

        List<FeedClothes> feedClothes = request.clothesIds()
                .stream()
                .distinct()
                .map(clothesId -> new FeedClothes(savedFeed, clothesId))
                .toList();

        feedClothesRepository.saveAll(feedClothes);

        return savedFeed.getId();
    }

    // 피드 수정
    @Transactional
    public void updateFeed(UUID feedId, UpdateFeedRequest request) {

        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 피드입니다."));

        feed.updateContent(request.content());
    }

    // 피드 삭제
    @Transactional
    public void deleteFeed(UUID feedId) {

        // 1. 삭제할 피드 조회
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 피드입니다."));

        // 2. 피드와 연결된 의상 정보 삭제
        feedClothesRepository.deleteByFeed_Id(feedId);

        // 3. 해당 피드의 좋아요 삭제
        feedLikeRepository.deleteAllByFeedId(feedId);

        // 4. 해당 피드의 댓글 삭제
        commentRepository.deleteAllByFeedId(feedId);

        // 5. 피드 삭제
        feedRepository.delete(feed);
    }
}
