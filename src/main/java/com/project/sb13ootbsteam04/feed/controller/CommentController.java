
package com.project.sb13ootbsteam04.feed.controller;

import com.project.sb13ootbsteam04.feed.dto.CommentPageResponse;
import com.project.sb13ootbsteam04.feed.dto.CommentResponse;
import com.project.sb13ootbsteam04.feed.dto.CreateCommentRequest;
import com.project.sb13ootbsteam04.feed.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    @GetMapping
    public ResponseEntity<CommentPageResponse> getComments(
            @PathVariable UUID feedId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam @Min(1) int limit
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
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable UUID feedId,
            @Valid @RequestBody CreateCommentRequest request
    ) {

        CommentResponse response = commentService.createCommentResponse(
                feedId,
                request
        );

        return ResponseEntity.ok(response);
    }
}
