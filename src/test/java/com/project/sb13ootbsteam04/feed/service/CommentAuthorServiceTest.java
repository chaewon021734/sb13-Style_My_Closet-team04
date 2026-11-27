package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.user.entity.*;
import com.project.sb13ootbsteam04.user.repository.*;
import com.project.sb13ootbsteam04.feed.dto.*;

import java.util.*;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentAuthorServiceTest {
    @Mock
    UserRepository userRepository;
    @Mock
    ProfileRepository profileRepository;
    @InjectMocks
    CommentAuthorService service;
    UUID id = UUID.randomUUID();

    @Test
    void nullId() {
        assertThrows(IllegalArgumentException.class, () -> service.getAuthor(null));
        verifyNoInteractions(userRepository);
    }

    @Test
    void missingUser() {
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getAuthor(id));
        verifyNoInteractions(profileRepository);
    }

    @Test
    void withProfile() {
        check(true);
    }

    @Test
    void withoutProfile() {
        check(false);
    }

    void check(boolean present) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getName()).thenReturn("author");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        if (present) {
            Profile profile = mock(Profile.class);
            when(profile.getProfileImageUrl()).thenReturn("image");
            when(profileRepository.findById(id)).thenReturn(Optional.of(profile));
        } else when(profileRepository.findById(id)).thenReturn(Optional.empty());
        assertEquals(new CommentResponse.AuthorDto(id, "author", present ? "image" : null), service.getAuthor(id));
    }
}
