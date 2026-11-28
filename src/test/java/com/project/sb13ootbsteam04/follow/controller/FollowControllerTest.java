package com.project.sb13ootbsteam04.follow.controller;

import com.project.sb13ootbsteam04.follow.dto.*;
import com.project.sb13ootbsteam04.follow.service.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowControllerTest {
    @Mock FollowService service;
    @InjectMocks FollowController controller;
    UUID user=UUID.randomUUID(), target=UUID.randomUUID(), id=UUID.randomUUID();
    JwtAuthenticationToken auth(String subject) { Jwt jwt=Jwt.withTokenValue("token").header("alg","HS256").subject(subject).build(); return new JwtAuthenticationToken(jwt,List.of()); }
    @Test void create() { CreateFollowRequest req=new CreateFollowRequest(target,user); FollowResponse dto=mock(FollowResponse.class); when(service.createFollowResponse(req)).thenReturn(dto); var r=controller.createFollow(req,auth(user.toString())); assertEquals(HttpStatus.CREATED,r.getStatusCode()); assertSame(dto,r.getBody()); }
    @Test void otherUser() { assertThrows(AccessDeniedException.class,()->controller.createFollow(new CreateFollowRequest(target,target),auth(user.toString()))); verifyNoInteractions(service); }
    @Test void noAuth() { assertThrows(AccessDeniedException.class,()->controller.deleteFollow(id,null)); verifyNoInteractions(service); }
    @Test void unauthenticated() { JwtAuthenticationToken token=mock(JwtAuthenticationToken.class); when(token.isAuthenticated()).thenReturn(false); assertThrows(AccessDeniedException.class,()->controller.deleteFollow(id,token)); verifyNoInteractions(service); }
    @Test void invalidSubject() { assertThrows(AccessDeniedException.class,()->controller.deleteFollow(id,auth("invalid"))); verifyNoInteractions(service); }
    @Test void delete() { assertEquals(HttpStatus.NO_CONTENT,controller.deleteFollow(id,auth(user.toString())).getStatusCode()); verify(service).deleteFollow(id,user); }
    @Test void summary() { FollowSummaryResponse dto=new FollowSummaryResponse(target,2,3,true,id,false); when(service.getFollowSummary(target,user)).thenReturn(dto); var r=controller.getFollowSummary(target,auth(user.toString())); assertEquals(HttpStatus.OK,r.getStatusCode()); assertSame(dto,r.getBody()); }
    @Test void followings() { FollowPageResponse dto=new FollowPageResponse(List.of(),null,null,false,0,"createdAt","DESCENDING"); when(service.getFollowingPage(user,null,null,10,"name")).thenReturn(dto); var r=controller.getFollowings(user,null,null,10,"name"); assertEquals(HttpStatus.OK,r.getStatusCode()); assertSame(dto,r.getBody()); }
    @Test void followers() { FollowPageResponse dto=new FollowPageResponse(List.of(),null,null,false,0,"createdAt","DESCENDING"); when(service.getFollowerPage(target,null,null,10,null)).thenReturn(dto); var r=controller.getFollowers(target,null,null,10,null); assertEquals(HttpStatus.OK,r.getStatusCode()); assertSame(dto,r.getBody()); }
}
