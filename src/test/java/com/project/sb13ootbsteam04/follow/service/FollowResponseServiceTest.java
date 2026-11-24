package com.project.sb13ootbsteam04.follow.service;

import com.project.sb13ootbsteam04.user.entity.*;
import com.project.sb13ootbsteam04.user.repository.*;
import com.project.sb13ootbsteam04.follow.dto.*;
import com.project.sb13ootbsteam04.follow.entity.*;

import java.util.*;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowResponseServiceTest {
    @Mock
    UserRepository userRepository;
    @Mock
    ProfileRepository profileRepository;
    @InjectMocks
    FollowResponseService service;
    UUID follower = UUID.randomUUID(), followee = UUID.randomUUID(), id = UUID.randomUUID();

    @Test
    void nullFollow() {
        assertThrows(IllegalArgumentException.class, () -> service.toResponse(null));
    }

    @Test
    void missingUser() {
        Follow f = new Follow(follower, followee);
        when(userRepository.findById(followee)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.toResponse(f));
    }

    @Test
    void mapsBothUsers() {
        Follow f = mock(Follow.class);
        when(f.getId()).thenReturn(id);
        when(f.getFollowerId()).thenReturn(follower);
        when(f.getFollowingId()).thenReturn(followee);
        User a = mock(User.class), b = mock(User.class);
        when(a.getId()).thenReturn(followee);
        when(a.getName()).thenReturn("target");
        when(b.getId()).thenReturn(follower);
        when(b.getName()).thenReturn("source");
        when(userRepository.findById(followee)).thenReturn(Optional.of(a));
        when(userRepository.findById(follower)).thenReturn(Optional.of(b));
        Profile p = mock(Profile.class);
        when(p.getProfileImageUrl()).thenReturn("image");
        when(profileRepository.findById(followee)).thenReturn(Optional.of(p));
        when(profileRepository.findById(follower)).thenReturn(Optional.empty());
        assertEquals(new FollowResponse(id, new FollowResponse.UserDto(followee, "target", "image"), new FollowResponse.UserDto(follower, "source", null)), service.toResponse(f));
    }

    @Test
    void invalidEntity() {
        assertThrows(IllegalArgumentException.class, () -> new Follow(null, followee));
        assertThrows(IllegalArgumentException.class, () -> new Follow(follower, null));
        assertThrows(IllegalArgumentException.class, () -> new Follow(follower, follower));
    }
}
