
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.dto.CommentPageResponse;
import com.project.sb13ootbsteam04.feed.dto.CommentResponse;
import com.project.sb13ootbsteam04.feed.dto.CreateCommentRequest;
import com.project.sb13ootbsteam04.feed.entity.Comment;
import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.repository.CommentRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
public class CommentService {

    private final CommentRepository commentRepository;
    private final FeedRepository feedRepository;
    private final UserRepository userRepository;
    private final CommentAuthorService commentAuthorService;

    // 댓글 등록
    @Transactional
    public UUID createComment(UUID feedId, CreateCommentRequest request) {

        if (feedId == null) {
            throw new IllegalArgumentException("피드 ID는 필수입니다.");
        }

        if (request == null) {
            throw new IllegalArgumentException("댓글 등록 요청은 필수입니다.");
        }

        if (request.feedId() == null || !feedId.equals(request.feedId())) {
            throw new IllegalArgumentException(
                    "요청한 피드 ID와 댓글의 피드 ID가 일치하지 않습니다."
            );
        }

        if (request.authorId() == null) {
            throw new IllegalArgumentException("댓글 작성자 ID는 필수입니다.");
        }

        if (request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("댓글 내용은 필수입니다.");
        }

        if (!userRepository.existsById(request.authorId())) {
            throw new IllegalArgumentException(
                    "존재하지 않는 사용자입니다: " + request.authorId()
            );
        }

        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 피드입니다: " + feedId
                        ));

        Comment comment = new Comment(
                feed,
                request.authorId(),
                request.content()
        );

        Comment savedComment = commentRepository.save(comment);

        return savedComment.getId();
    }

    // 기존 댓글 목록 조회
    public Page<Comment> getComments(UUID feedId, Pageable pageable) {

        if (!feedRepository.existsById(feedId)) {
            throw new IllegalArgumentException(
                    "존재하지 않는 피드입니다: " + feedId
            );
        }

        return commentRepository.findByFeed_Id(feedId, pageable);
    }

    // 댓글 수 조회
    public long getCommentCount(UUID feedId) {
        return commentRepository.countByFeed_Id(feedId);
    }

    // 댓글 엔티티를 응답 DTO로 변환
    public CommentResponse toCommentResponse(Comment comment) {

        if (comment == null) {
            throw new IllegalArgumentException("댓글 정보는 필수입니다.");
        }

        CommentResponse.AuthorDto author =
                commentAuthorService.getAuthor(comment.getUserId());

        return new CommentResponse(
                comment.getId(),
                comment.getCreatedAt(),
                comment.getFeed().getId(),
                author,
                comment.getContent()
        );
    }

    // 기존 Pageable 기반 댓글 응답 조회
    public Page<CommentResponse> getCommentResponses(
            UUID feedId,
            Pageable pageable
    ) {
        return getComments(feedId, pageable)
                .map(this::toCommentResponse);
    }

    // 기존 Pageable 기반 페이지 응답
    public CommentPageResponse getCommentPage(
            UUID feedId,
            Pageable pageable
    ) {

        Page<CommentResponse> page =
                getCommentResponses(feedId, pageable);

        List<CommentResponse> data = page.getContent();

        boolean hasNext = page.hasNext();

        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !data.isEmpty()) {
            CommentResponse last = data.get(data.size() - 1);
            nextCursor = last.createdAt().toString();
            nextIdAfter = last.id();
        }

        String sortBy = pageable.getSort().isSorted()
                ? pageable.getSort().iterator().next().getProperty()
                : "createdAt";

        String sortDirection = pageable.getSort().isSorted()
                ? pageable.getSort().iterator().next().getDirection().name()
                : "DESC";

        return new CommentPageResponse(
                data,
                nextCursor,
                nextIdAfter,
                hasNext,
                page.getTotalElements(),
                sortBy,
                sortDirection
        );
    }

    // Swagger 파라미터에 맞춘 커서 기반 댓글 조회
    public CommentPageResponse getCommentPage(
            UUID feedId,
            String cursor,
            UUID idAfter,
            int limit
    ) {

        // 1. 요청값 검증
        if (feedId == null) {
            throw new IllegalArgumentException("피드 ID는 필수입니다.");
        }

        if (limit < 1 || limit == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "limit은 1 이상이며 허용 범위 내의 값이어야 합니다."
            );
        }

        if (!feedRepository.existsById(feedId)) {
            throw new IllegalArgumentException(
                    "존재하지 않는 피드입니다: " + feedId
            );
        }

        // 2. 커서 검증
        boolean hasCursor = cursor != null && !cursor.isBlank();

        if (hasCursor != (idAfter != null)) {
            throw new IllegalArgumentException(
                    "cursor와 idAfter는 함께 전달해야 합니다."
            );
        }

        // 3. limit + 1개 조회
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<Comment> comments;

        if (!hasCursor) {
            comments = commentRepository.findFirstPage(
                    feedId,
                    pageable
            );
        } else {
            Instant cursorTime;

            try {
                cursorTime = Instant.parse(cursor);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "cursor는 ISO-8601 날짜 형식이어야 합니다.",
                        e
                );
            }

            comments = commentRepository.findNextPage(
                    feedId,
                    cursorTime,
                    idAfter,
                    pageable
            );
        }

        // 4. 다음 페이지 존재 여부 확인
        boolean hasNext = comments.size() > limit;

        List<Comment> pageComments = comments.stream()
                .limit(limit)
                .toList();

        // 5. 응답 DTO 변환
        List<CommentResponse> data = pageComments.stream()
                .map(this::toCommentResponse)
                .toList();

        // 6. 다음 커서 생성
        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !pageComments.isEmpty()) {
            Comment last = pageComments.get(pageComments.size() - 1);
            nextCursor = last.getCreatedAt().toString();
            nextIdAfter = last.getId();
        }

        // 7. 전체 댓글 수 조회
        long totalCount = commentRepository.countByFeed_Id(feedId);

        // 8. Swagger 응답 DTO 반환
        return new CommentPageResponse(
                data,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                "createdAt",
                "DESC"
        );
    }


    // 댓글 등록 후 Swagger 응답 형식으로 반환
    @Transactional
    public CommentResponse createCommentResponse(
            UUID feedId,
            CreateCommentRequest request
    ) {

        // 1. 기존 댓글 등록 기능 호출
        UUID commentId = createComment(feedId, request);

        // 2. 저장된 댓글 조회
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "등록된 댓글을 찾을 수 없습니다: " + commentId
                        ));

        // 3. 작성자 정보를 포함한 응답 DTO 반환
        return toCommentResponse(comment);
    }

}
