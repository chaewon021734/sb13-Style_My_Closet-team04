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
class CommentControllerTest {
    @Mock CommentService service;
    @Mock JwtUserIdResolver resolver;
    @Mock Authentication auth;
    @InjectMocks CommentController controller;
    UUID id=UUID.randomUUID(),userId=UUID.randomUUID();
    @Test void list() { CommentPageResponse dto=new CommentPageResponse(List.of(),null,null,false,0,"createdAt","DESC"); when(service.getCommentPage(id,null,null,10)).thenReturn(dto); var r=controller.getComments(id,null,null,10); assertEquals(HttpStatus.OK,r.getStatusCode()); assertSame(dto,r.getBody()); }
    @Test void create() { when(resolver.resolve(auth)).thenReturn(userId); CreateCommentRequest req=new CreateCommentRequest(id,userId,"comment"); CommentResponse dto=mock(CommentResponse.class); when(service.createCommentResponse(id,req,userId)).thenReturn(dto); var r=controller.createComment(id,req,auth); assertEquals(HttpStatus.CREATED,r.getStatusCode()); assertSame(dto,r.getBody()); }
}
