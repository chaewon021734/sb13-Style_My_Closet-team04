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
class FeedControllerTest {
    @Mock FeedService service;
    @Mock JwtUserIdResolver resolver;
    @Mock Authentication auth;
    @InjectMocks FeedController controller;
    UUID id=UUID.randomUUID(),userId=UUID.randomUUID();
    @org.junit.jupiter.api.BeforeEach void setup() { when(resolver.resolve(auth)).thenReturn(userId); }
    @Test void list() { FeedPageResponse page=new FeedPageResponse(List.of(),null,null,false,0,"createdAt","DESCENDING"); when(service.getFeedPage(null,null,10,"createdAt","DESCENDING","shirt","CLEAR","NONE",userId,userId)).thenReturn(page); var r=controller.getFeeds(null,null,10,"createdAt","DESCENDING","shirt","CLEAR","NONE",userId,auth); assertEquals(HttpStatus.OK,r.getStatusCode()); assertSame(page,r.getBody()); }
    @Test void create() { CreateFeedRequest req=new CreateFeedRequest(userId,UUID.randomUUID(),List.of(),"post"); FeedResponse dto=mock(FeedResponse.class); when(service.createFeed(req,userId)).thenReturn(id); when(service.getFeedResponse(id,userId)).thenReturn(dto); var r=controller.createFeed(req,auth); assertEquals(HttpStatus.CREATED,r.getStatusCode()); assertEquals("/api/feeds/"+id,r.getHeaders().getLocation().toString()); assertSame(dto,r.getBody()); }
    @Test void update() { UpdateFeedRequest req=new UpdateFeedRequest("new"); FeedResponse dto=mock(FeedResponse.class); when(service.getFeedResponse(id,userId)).thenReturn(dto); var r=controller.updateFeed(id,req,auth); assertEquals(HttpStatus.OK,r.getStatusCode()); assertSame(dto,r.getBody()); verify(service).updateFeed(id,req,userId); }
    @Test void delete() { assertEquals(HttpStatus.NO_CONTENT,controller.deleteFeed(id,auth).getStatusCode()); verify(service).deleteFeed(id,userId); }
}
