
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.dto.CommentPageResponse;
import com.project.sb13ootbsteam04.feed.dto.CommentResponse;
import com.project.sb13ootbsteam04.feed.dto.CreateCommentRequest;
import com.project.sb13ootbsteam04.feed.entity.Comment;
import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.repository.CommentRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentAuthorService commentAuthorService;

    @InjectMocks
    private CommentService commentService;

    private final UUID feedId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID commentId = UUID.randomUUID();

    private final Instant createdAt =
            Instant.parse("2026-11-24T06:00:00Z");

    // ========================================
    // 댓글 등록 테스트
    // ========================================

    @Test
    void 로그인하지_않으면_댓글_등록_예외() {
        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> commentService.createComment(
                        feedId, null, null
                )
        );

        assertEquals(
                "로그인이 필요합니다.",
                exception.getMessage()
        );

        verifyNoInteractions(
                commentRepository,
                feedRepository,
                userRepository
        );
    }

    @Test
    void 피드_ID가_없으면_예외() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createComment(
                        null, null, userId
                )
        );

        assertEquals(
                "피드 ID는 필수입니다.",
                exception.getMessage()
        );
    }

    @Test
    void 댓글_등록_요청이_없으면_예외() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createComment(
                        feedId, null, userId
                )
        );

        assertEquals(
                "댓글 등록 요청은 필수입니다.",
                exception.getMessage()
        );
    }

    @Test
    void 요청의_피드_ID가_null이면_예외() {
        CreateCommentRequest request =
                mock(CreateCommentRequest.class);

        when(request.feedId()).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createComment(
                        feedId, request, userId
                )
        );

        assertEquals(
                "요청한 피드 ID와 댓글의 피드 ID가 일치하지 않습니다.",
                exception.getMessage()
        );
    }

    @Test
    void 요청의_피드_ID가_다르면_예외() {
        CreateCommentRequest request =
                mock(CreateCommentRequest.class);

        when(request.feedId())
                .thenReturn(UUID.randomUUID());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createComment(
                        feedId, request, userId
                )
        );

        assertEquals(
                "요청한 피드 ID와 댓글의 피드 ID가 일치하지 않습니다.",
                exception.getMessage()
        );
    }

    @Test
    void 댓글_작성자_ID가_없으면_예외() {
        CreateCommentRequest request =
                mock(CreateCommentRequest.class);

        when(request.feedId()).thenReturn(feedId);
        when(request.authorId()).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createComment(
                        feedId, request, userId
                )
        );

        assertEquals(
                "댓글 작성자 ID는 필수입니다.",
                exception.getMessage()
        );
    }

    @Test
    void 다른_사용자_계정으로_댓글을_등록하면_예외() {
        CreateCommentRequest request =
                mock(CreateCommentRequest.class);

        when(request.feedId()).thenReturn(feedId);
        when(request.authorId())
                .thenReturn(UUID.randomUUID());

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> commentService.createComment(
                        feedId, request, userId
                )
        );

        assertEquals(
                "본인의 계정으로만 댓글을 등록할 수 있습니다.",
                exception.getMessage()
        );

        verifyNoInteractions(commentRepository);
    }

    @Test
    void 댓글_내용이_null이면_예외() {
        CreateCommentRequest request =
                createRequest(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createComment(
                        feedId, request, userId
                )
        );

        assertEquals(
                "댓글 내용은 필수입니다.",
                exception.getMessage()
        );
    }

    @Test
    void 댓글_내용이_공백이면_예외() {
        CreateCommentRequest request =
                createRequest("   ");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createComment(
                        feedId, request, userId
                )
        );

        assertEquals(
                "댓글 내용은 필수입니다.",
                exception.getMessage()
        );
    }

    @Test
    void 존재하지_않는_사용자이면_예외() {
        CreateCommentRequest request =
                createRequest("테스트 댓글");

        when(userRepository.existsById(userId))
                .thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createComment(
                        feedId, request, userId
                )
        );

        assertEquals(
                "존재하지 않는 사용자입니다: " + userId,
                exception.getMessage()
        );

        verifyNoInteractions(
                feedRepository,
                commentRepository
        );
    }

    @Test
    void 존재하지_않는_피드에_댓글을_등록하면_예외() {
        CreateCommentRequest request =
                createRequest("테스트 댓글");

        when(userRepository.existsById(userId))
                .thenReturn(true);

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createComment(
                        feedId, request, userId
                )
        );

        assertEquals(
                "존재하지 않는 피드입니다: " + feedId,
                exception.getMessage()
        );

        verifyNoInteractions(commentRepository);
    }

    @Test
    void 댓글_등록_성공() {
        CreateCommentRequest request =
                createRequest("테스트 댓글");

        Feed feed = mock(Feed.class);
        Comment saved = mock(Comment.class);

        when(userRepository.existsById(userId))
                .thenReturn(true);

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        when(saved.getId()).thenReturn(commentId);

        when(commentRepository.save(any(Comment.class)))
                .thenReturn(saved);

        UUID result = commentService.createComment(
                feedId, request, userId
        );

        assertEquals(commentId, result);

        ArgumentCaptor<Comment> captor =
                ArgumentCaptor.forClass(Comment.class);

        verify(commentRepository).save(captor.capture());

        Comment captured = captor.getValue();

        assertSame(feed, captured.getFeed());
        assertEquals(userId, captured.getUserId());
        assertEquals("테스트 댓글", captured.getContent());
    }

    // ========================================
    // 댓글 목록 및 개수 조회
    // ========================================

    @Test
    void 댓글_목록_조회_성공() {
        Pageable pageable = PageRequest.of(0, 10);

        Comment comment = mock(Comment.class);

        Page<Comment> expected =
                new PageImpl<>(List.of(comment));

        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findByFeed_Id(
                feedId, pageable
        )).thenReturn(expected);

        Page<Comment> result =
                commentService.getComments(feedId, pageable);

        assertSame(expected, result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void 존재하지_않는_피드의_댓글_조회시_예외() {
        when(feedRepository.existsById(feedId))
                .thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.getComments(
                        feedId, PageRequest.of(0, 10)
                )
        );

        assertEquals(
                "존재하지 않는 피드입니다: " + feedId,
                exception.getMessage()
        );

        verifyNoInteractions(commentRepository);
    }

    @Test
    void 댓글_개수_조회_성공() {
        when(commentRepository.countByFeed_Id(feedId))
                .thenReturn(5L);

        assertEquals(
                5L,
                commentService.getCommentCount(feedId)
        );

        verify(commentRepository).countByFeed_Id(feedId);
    }

    @Test
    void 댓글이_없으면_개수는_0이다() {
        when(commentRepository.countByFeed_Id(feedId))
                .thenReturn(0L);

        assertEquals(
                0L,
                commentService.getCommentCount(feedId)
        );
    }

    // ========================================
    // 댓글 응답 DTO 변환
    // ========================================

    @Test
    void 댓글_정보가_null이면_변환_예외() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.toCommentResponse(null)
        );

        assertEquals(
                "댓글 정보는 필수입니다.",
                exception.getMessage()
        );

        verifyNoInteractions(commentAuthorService);
    }

    @Test
    void 댓글_응답_DTO_변환_성공() {
        Comment comment = mockComment();

        CommentResponse.AuthorDto author =
                mock(CommentResponse.AuthorDto.class);

        when(commentAuthorService.getAuthor(userId))
                .thenReturn(author);

        CommentResponse result =
                commentService.toCommentResponse(comment);

        assertNotNull(result);
        assertEquals(commentId, result.id());
        assertEquals(createdAt, result.createdAt());
        assertEquals(feedId, result.feedId());
        assertSame(author, result.author());
        assertEquals("테스트 댓글", result.content());
    }

    @Test
    void 댓글_응답_목록_조회_성공() {
        Pageable pageable = PageRequest.of(0, 10);

        Comment comment = mockComment();

        CommentResponse.AuthorDto author =
                mock(CommentResponse.AuthorDto.class);

        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findByFeed_Id(
                feedId, pageable
        )).thenReturn(new PageImpl<>(List.of(comment)));

        when(commentAuthorService.getAuthor(userId))
                .thenReturn(author);

        Page<CommentResponse> result =
                commentService.getCommentResponses(
                        feedId, pageable
                );

        assertEquals(1, result.getTotalElements());
        assertEquals(
                commentId,
                result.getContent().get(0).id()
        );
    }

    // ========================================
    // Pageable 기반 페이지네이션
    // ========================================

    @Test
    void Pageable_기본_정렬로_조회() {
        Pageable pageable = PageRequest.of(0, 10);

        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findByFeed_Id(
                feedId, pageable
        )).thenReturn(Page.empty(pageable));

        CommentPageResponse result =
                commentService.getCommentPage(
                        feedId, pageable
                );

        assertFalse(result.hasNext());
        assertEquals(0L, result.totalCount());
        assertEquals("createdAt", result.sortBy());
        assertEquals("DESC", result.sortDirection());
        assertNull(result.nextCursor());
        assertNull(result.nextIdAfter());
    }

    @Test
    void Pageable_정렬_조건을_반영한다() {
        Pageable pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findByFeed_Id(
                feedId, pageable
        )).thenReturn(Page.empty(pageable));

        CommentPageResponse result =
                commentService.getCommentPage(
                        feedId, pageable
                );

        assertEquals("createdAt", result.sortBy());
        assertEquals("ASC", result.sortDirection());
        assertFalse(result.hasNext());
    }

    @Test
    void Pageable_다음페이지가_있으면_커서를_생성한다() {
        Pageable pageable = PageRequest.of(0, 1);

        Comment comment = mockComment();

        CommentResponse.AuthorDto author =
                mock(CommentResponse.AuthorDto.class);

        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findByFeed_Id(
                feedId, pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(comment),
                        pageable,
                        2L
                )
        );

        when(commentAuthorService.getAuthor(userId))
                .thenReturn(author);

        CommentPageResponse result =
                commentService.getCommentPage(
                        feedId, pageable
                );

        assertTrue(result.hasNext());
        assertEquals(2L, result.totalCount());
        assertEquals(1, result.data().size());
        assertEquals(
                createdAt.toString(),
                result.nextCursor()
        );
        assertEquals(commentId, result.nextIdAfter());
    }

    // ========================================
    // 커서 기반 페이지네이션
    // ========================================

    @Test
    void 커서_조회시_피드_ID가_null이면_예외() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.getCommentPage(
                        null, null, null, 10
                )
        );

        assertEquals(
                "피드 ID는 필수입니다.",
                exception.getMessage()
        );
    }

    @Test
    void limit이_0이면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> commentService.getCommentPage(
                        feedId, null, null, 0
                )
        );

        verifyNoInteractions(feedRepository);
    }

    @Test
    void limit이_101이면_예외() {
        assertThrows(
                IllegalArgumentException.class,
                () -> commentService.getCommentPage(
                        feedId, null, null, 101
                )
        );

        verifyNoInteractions(feedRepository);
    }

    @Test
    void 커서_조회시_존재하지_않는_피드이면_예외() {
        when(feedRepository.existsById(feedId))
                .thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> commentService.getCommentPage(
                        feedId, null, null, 10
                )
        );

        verifyNoInteractions(commentRepository);
    }

    @Test
    void cursor만_전달하면_예외() {
        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.getCommentPage(
                        feedId,
                        createdAt.toString(),
                        null,
                        10
                )
        );

        assertEquals(
                "cursor와 idAfter는 함께 전달해야 합니다.",
                exception.getMessage()
        );
    }

    @Test
    void idAfter만_전달하면_예외() {
        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> commentService.getCommentPage(
                        feedId, null, commentId, 10
                )
        );
    }

    @Test
    void 잘못된_날짜_커서이면_예외() {
        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.getCommentPage(
                        feedId,
                        "invalid-cursor",
                        commentId,
                        10
                )
        );

        assertEquals(
                "cursor는 ISO-8601 날짜 형식이어야 합니다.",
                exception.getMessage()
        );

        verifyNoInteractions(commentRepository);
    }

    @Test
    void 커서_첫페이지_조회_성공() {
        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findFirstPage(
                feedId, PageRequest.of(0, 11)
        )).thenReturn(List.of());

        CommentPageResponse result =
                commentService.getCommentPage(
                        feedId, null, null, 10
                );

        assertFalse(result.hasNext());
        assertEquals(0L, result.totalCount());
        assertTrue(result.data().isEmpty());
        assertNull(result.nextCursor());
        assertNull(result.nextIdAfter());
        assertEquals("createdAt", result.sortBy());
        assertEquals("DESC", result.sortDirection());
    }

    @Test
    void 빈_커서는_첫페이지로_처리한다() {
        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findFirstPage(
                feedId, PageRequest.of(0, 11)
        )).thenReturn(List.of());

        CommentPageResponse result =
                commentService.getCommentPage(
                        feedId, "   ", null, 10
                );

        assertFalse(result.hasNext());

        verify(commentRepository).findFirstPage(
                feedId, PageRequest.of(0, 11)
        );
    }

    @Test
    void 커서_다음페이지_조회_성공() {
        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findNextPage(
                feedId,
                createdAt,
                commentId,
                PageRequest.of(0, 11)
        )).thenReturn(List.of());

        CommentPageResponse result =
                commentService.getCommentPage(
                        feedId,
                        createdAt.toString(),
                        commentId,
                        10
                );

        assertFalse(result.hasNext());

        verify(commentRepository).findNextPage(
                feedId,
                createdAt,
                commentId,
                PageRequest.of(0, 11)
        );
    }

    @Test
    void 커서_다음페이지가_있으면_커서를_생성한다() {
        Comment first = mockComment();
        Comment second = mock(Comment.class);

        CommentResponse.AuthorDto author =
                mock(CommentResponse.AuthorDto.class);

        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findFirstPage(
                feedId, PageRequest.of(0, 2)
        )).thenReturn(List.of(first, second));

        when(commentRepository.countByFeed_Id(feedId))
                .thenReturn(2L);

        when(commentAuthorService.getAuthor(userId))
                .thenReturn(author);

        CommentPageResponse result =
                commentService.getCommentPage(
                        feedId, null, null, 1
                );

        assertTrue(result.hasNext());
        assertEquals(2L, result.totalCount());
        assertEquals(1, result.data().size());

        assertEquals(
                createdAt.toString(),
                result.nextCursor()
        );

        assertEquals(commentId, result.nextIdAfter());

        verifyNoInteractions(second);
    }

    @Test
    void 커서_마지막페이지는_다음커서가_없다() {
        Comment comment = mockComment();

        CommentResponse.AuthorDto author =
                mock(CommentResponse.AuthorDto.class);

        when(feedRepository.existsById(feedId))
                .thenReturn(true);

        when(commentRepository.findFirstPage(
                feedId, PageRequest.of(0, 11)
        )).thenReturn(List.of(comment));

        when(commentRepository.countByFeed_Id(feedId))
                .thenReturn(1L);

        when(commentAuthorService.getAuthor(userId))
                .thenReturn(author);

        CommentPageResponse result =
                commentService.getCommentPage(
                        feedId, null, null, 10
                );

        assertFalse(result.hasNext());
        assertEquals(1L, result.totalCount());
        assertEquals(1, result.data().size());
        assertNull(result.nextCursor());
        assertNull(result.nextIdAfter());
    }

    // ========================================
    // 댓글 등록 후 응답 반환
    // ========================================

    @Test
    void 댓글_등록후_응답_반환_성공() {
        CreateCommentRequest request =
                createRequest("테스트 댓글");

        Comment comment = mockComment();

        Feed feed = mock(Feed.class);

        CommentResponse.AuthorDto author =
                mock(CommentResponse.AuthorDto.class);

        when(userRepository.existsById(userId))
                .thenReturn(true);

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        when(commentRepository.save(any(Comment.class)))
                .thenReturn(comment);

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        when(commentAuthorService.getAuthor(userId))
                .thenReturn(author);

        CommentResponse result =
                commentService.createCommentResponse(
                        feedId, request, userId
                );

        assertNotNull(result);
        assertEquals(commentId, result.id());
        assertEquals(feedId, result.feedId());
        assertEquals("테스트 댓글", result.content());
        assertSame(author, result.author());
    }

    @Test
    void 등록된_댓글을_찾을수_없으면_예외() {
        CreateCommentRequest request =
                createRequest("테스트 댓글");

        Feed feed = mock(Feed.class);
        Comment saved = mock(Comment.class);

        when(userRepository.existsById(userId))
                .thenReturn(true);

        when(feedRepository.findById(feedId))
                .thenReturn(Optional.of(feed));

        when(saved.getId()).thenReturn(commentId);

        when(commentRepository.save(any(Comment.class)))
                .thenReturn(saved);

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commentService.createCommentResponse(
                        feedId, request, userId
                )
        );

        assertEquals(
                "등록된 댓글을 찾을 수 없습니다: " + commentId,
                exception.getMessage()
        );

        verifyNoInteractions(commentAuthorService);
    }

    // ========================================
    // 공통 Mock 설정
    // ========================================

    private CreateCommentRequest createRequest(String content) {
        CreateCommentRequest request =
                mock(CreateCommentRequest.class);

        when(request.feedId()).thenReturn(feedId);
        when(request.authorId()).thenReturn(userId);
        when(request.content()).thenReturn(content);

        return request;
    }

    private Comment mockComment() {
        Comment comment = mock(Comment.class);
        Feed feed = mock(Feed.class);

        when(comment.getId()).thenReturn(commentId);
        when(comment.getUserId()).thenReturn(userId);
        when(comment.getCreatedAt()).thenReturn(createdAt);
        when(comment.getContent()).thenReturn("테스트 댓글");
        when(comment.getFeed()).thenReturn(feed);
        when(feed.getId()).thenReturn(feedId);

        return comment;
    }
}
