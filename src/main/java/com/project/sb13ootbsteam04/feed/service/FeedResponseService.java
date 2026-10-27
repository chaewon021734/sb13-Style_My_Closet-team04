
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.dto.FeedResponse;
import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.repository.CommentRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedLikeRepository;
import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedResponseService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final CommentRepository commentRepository;

    // 피드 엔티티를 Swagger 응답 DTO로 변환
    public FeedResponse toResponse(Feed feed) {

        if (feed == null) {
            throw new IllegalArgumentException(
                    "피드 정보는 필수입니다."
            );
        }

        // 1. 작성자 조회
        User author = userRepository.findById(feed.getUserId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "피드 작성자를 찾을 수 없습니다: "
                                        + feed.getUserId()
                        )
                );

        // 2. 작성자 프로필 이미지 조회
        String profileImageUrl = profileRepository
                .findById(author.getId())
                .map(Profile::getProfileImageUrl)
                .orElse(null);

        FeedResponse.AuthorDto authorDto =
                new FeedResponse.AuthorDto(
                        author.getId(),
                        author.getName(),
                        profileImageUrl
                );

        // 3. 좋아요 수 조회
        long likeCount = feedLikeRepository.countByFeed_Id(
                feed.getId()
        );

        // 4. 댓글 수 조회
        long commentCount = commentRepository.countByFeed_Id(
                feed.getId()
        );

        // 5. Swagger 응답 DTO 생성
        return new FeedResponse(
                feed.getId(),
                feed.getCreatedAt(),
                feed.getUpdatedAt(),
                authorDto,
                null,
                List.of(),
                feed.getContent(),
                likeCount,
                Math.toIntExact(commentCount),
                false
        );
    }

    // 로그인 사용자 정보가 있는 경우 좋아요 여부까지 반영
    public FeedResponse toResponse(
            Feed feed,
            UUID currentUserId
    ) {

        FeedResponse response = toResponse(feed);

        if (currentUserId == null) {
            return response;
        }

        boolean likedByMe =
                feedLikeRepository.existsByFeed_IdAndUserId(
                        feed.getId(),
                        currentUserId
                );

        return new FeedResponse(
                response.id(),
                response.createdAt(),
                response.updatedAt(),
                response.author(),
                response.weather(),
                response.ootds(),
                response.content(),
                response.likeCount(),
                response.commentCount(),
                likedByMe
        );
    }
}
