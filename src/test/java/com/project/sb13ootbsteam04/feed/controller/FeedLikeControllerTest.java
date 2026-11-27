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
}
