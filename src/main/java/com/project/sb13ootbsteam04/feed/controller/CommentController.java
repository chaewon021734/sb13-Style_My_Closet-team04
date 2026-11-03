
package com.project.sb13ootbsteam04.feed.controller;

import com.project.sb13ootbsteam04.feed.dto.CommentPageResponse;
import com.project.sb13ootbsteam04.feed.dto.CommentResponse;
import com.project.sb13ootbsteam04.feed.dto.CreateCommentRequest;
import com.project.sb13ootbsteam04.feed.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/feeds/{feedId}/comments")
@RequiredArgsConstructor
@Validated
public class CommentController {

    private final CommentService commentService;

    // 댓글 목록 조회
    // GET /api/feeds/{feedId}/comments
    @GetMapping
    public ResponseEntity<CommentPageResponse> getComments(
            @PathVariable UUID feedId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam @Min(1) @Max(100) int limit
    ) {

        CommentPageResponse response = commentService.getCommentPage(
                feedId,
                cursor,
                idAfter,
                limit
        );

        return ResponseEntity.ok(response);
    }

    // 댓글 등록
    // POST /api/feeds/{feedId}/comments
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable UUID feedId,
            @Valid @RequestBody CreateCommentRequest request,
            Authentication authentication
    ) {

        // JWT에서 로그인 사용자 UUID 추출
        UUID currentUserId = getCurrentUserId(authentication);

        // 댓글 등록 및 응답 DTO 반환
        CommentResponse response = commentService.createCommentResponse(
                feedId,
                request,
                currentUserId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // JWT에서 로그인 사용자 UUID 추출
    private UUID getCurrentUserId(Authentication authentication) {

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "로그인이 필요합니다."
            );
        }

        try {
            return UUID.fromString(
                    jwtAuth.getToken().getSubject()
            );
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException(
                    "유효하지 않은 사용자 인증 정보입니다."
            );
        }
    }
}
