
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.dto.CreateCommentRequest;
import com.project.sb13ootbsteam04.feed.entity.Comment;
import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.repository.CommentRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final FeedRepository feedRepository;

    // 댓글 등록
    @Transactional
    public UUID createComment(UUID feedId, CreateCommentRequest request) {

        // URL의 피드 ID와 요청 본문의 피드 ID 비교
        if (request.feedId() == null || !feedId.equals(request.feedId())) {
            throw new IllegalArgumentException("피드 ID가 일치하지 않습니다.");
        }

        if (request.authorId() == null) {
            throw new IllegalArgumentException("댓글 작성자 ID는 필수입니다.");
        }

        if (request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("댓글 내용은 필수입니다.");
        }

        // 댓글을 등록할 피드 조회
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 피드입니다."));

        // 댓글 생성 및 저장
        Comment comment = new Comment(
                feed,
                request.authorId(),
                request.content()
        );

        Comment savedComment = commentRepository.save(comment);

        return savedComment.getId();
    }

    // 특정 피드의 댓글 목록 조회
    public Page<Comment> getComments(UUID feedId, Pageable pageable) {

        if (!feedRepository.existsById(feedId)) {
            throw new IllegalArgumentException("존재하지 않는 피드입니다.");
        }

        return commentRepository.findByFeed_Id(feedId, pageable);
    }

    // 특정 피드의 댓글 개수 조회
    public long getCommentCount(UUID feedId) {
        return commentRepository.countByFeed_Id(feedId);
    }
}
