package com.project.sb13ootbsteam04.feed.controller;

import com.project.sb13ootbsteam04.feed.dto.*;
import com.project.sb13ootbsteam04.feed.service.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedLikeControllerTest {
    @Mock FeedLikeService service;
    @Mock JwtUserIdResolver resolver;
    @Mock Authentication auth;
    @InjectMocks FeedLikeController controller;
    UUID id=UUID.randomUUID(),userId=UUID.randomUUID();
    @org.junit.jupiter.api.BeforeEach void setup() { when(resolver.resolve(auth)).thenReturn(userId); }
    @Test void like() { assertEquals(HttpStatus.NO_CONTENT,controller.likeFeed(id,auth).getStatusCode()); verify(service).likeFeed(id,userId); }
    @Test void unlike() { assertEquals(HttpStatus.NO_CONTENT,controller.unlikeFeed(id,auth).getStatusCode()); verify(service).unlikeFeed(id,userId); }

    // 동시 요청이 같은 좋아요를 먼저 저장했으면 중복 제약 예외여도 성공 응답
    @Test void likeSucceedsWhenConcurrentRequestAlreadySaved() {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("uk_feed_likes_feed_user"))
                .when(service).likeFeed(id,userId);
        when(service.isLikedByMe(id,userId)).thenReturn(true);
        assertEquals(HttpStatus.NO_CONTENT,controller.likeFeed(id,auth).getStatusCode());
    }

    // 좋아요가 저장되지 않은 무결성 예외는 그대로 전달
    @Test void likeRethrowsWhenLikeWasNotSaved() {
        var failure=new org.springframework.dao.DataIntegrityViolationException("fk_feed_likes_feed");
        doThrow(failure).when(service).likeFeed(id,userId);
        when(service.isLikedByMe(id,userId)).thenReturn(false);
        assertSame(failure,assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->controller.likeFeed(id,auth)));
    }
}
