package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.user.entity.*;
import com.project.sb13ootbsteam04.user.repository.*;
import com.project.sb13ootbsteam04.feed.dto.*;
import com.project.sb13ootbsteam04.feed.entity.*;
import com.project.sb13ootbsteam04.feed.repository.*;
import com.project.sb13ootbsteam04.clothes.entity.*;
import com.project.sb13ootbsteam04.clothes.repository.*;
import com.project.sb13ootbsteam04.weather.entity.*;
import com.project.sb13ootbsteam04.weather.repository.*;

import java.util.*;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedResponseServiceTest {
    @Mock
    UserRepository userRepository;
    @Mock
    ProfileRepository profileRepository;
    @Mock
    FeedLikeRepository feedLikeRepository;
    @Mock
    CommentRepository commentRepository;
    @Mock
    WeatherRepository weatherRepository;
    @Mock
    FeedClothesRepository feedClothesRepository;
    @Mock
    ClothesRepository clothesRepository;
    @InjectMocks
    FeedResponseService service;
    UUID id = UUID.randomUUID(), userId = UUID.randomUUID(), weatherId = UUID.randomUUID();
    Instant time = Instant.parse("2026-11-24T00:00:00Z");

    @Test
    void nullFeed() {
        assertThrows(IllegalArgumentException.class, () -> service.toResponse(null));
    }

    @Test
    void missingAuthor() {
        Feed f = new Feed(userId, null, "post");
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.toResponse(f));
    }

    Feed setup(UUID wid) {
        Feed f = mock(Feed.class);
        when(f.getId()).thenReturn(id);
        when(f.getUserId()).thenReturn(userId);
        when(f.getWeatherId()).thenReturn(wid);
        when(f.getContent()).thenReturn("post");
        when(f.getCreatedAt()).thenReturn(time);
        when(f.getUpdatedAt()).thenReturn(time);
        User u = mock(User.class);
        when(u.getId()).thenReturn(userId);
        when(u.getName()).thenReturn("author");
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(feedLikeRepository.countByFeed_Id(id)).thenReturn(3L);
        when(commentRepository.countByFeed_Id(id)).thenReturn(2L);
        return f;
    }

    @Test
    void anonymousWithoutWeatherOrClothes() {
        Feed f = setup(null);
        FeedResponse r = service.toResponse(f, null);
        assertEquals(new FeedResponse(id, time, time, new FeedResponse.AuthorDto(userId, "author", null), null, List.of(), "post", 3L, 2, false), r);
        verifyNoInteractions(weatherRepository, clothesRepository);
        verify(feedLikeRepository, never()).existsByFeed_IdAndUserId(any(), any());
    }

    @Test
    void missingWeatherAndNotLiked() {
        Feed f = setup(weatherId);
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.empty());
        when(feedLikeRepository.existsByFeed_IdAndUserId(id, userId)).thenReturn(false);
        FeedResponse r = service.toResponse(f, userId);
        assertNull(r.weather());
        assertFalse(r.likedByMe());
    }

    @Test
    void fullResponsePreservesClothesOrderAndRemovesDuplicates() {
        Feed f = setup(weatherId);
        Profile p = mock(Profile.class);
        when(p.getProfileImageUrl()).thenReturn("profile");
        when(profileRepository.findById(userId)).thenReturn(Optional.of(p));
        Weather w = mock(Weather.class);
        when(w.getId()).thenReturn(weatherId);
        when(w.getSkyStatus()).thenReturn(SkyStatus.CLEAR);
        when(w.getPrecipitationType()).thenReturn(PrecipitationType.NONE);
        when(w.getPrecipitationAmount()).thenReturn(0.0);
        when(w.getPrecipitationProbability()).thenReturn(10.0);
        when(w.getTemperatureCurrent()).thenReturn(15.0);
        when(w.getTemperatureComparedToDayBefore()).thenReturn(1.0);
        when(w.getTemperatureMin()).thenReturn(8.0);
        when(w.getTemperatureMax()).thenReturn(18.0);
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.of(w));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), missing = UUID.randomUUID(), defId = UUID.randomUUID();
        ClothesAttributeDef def = mock(ClothesAttributeDef.class);
        when(def.getId()).thenReturn(defId);
        when(def.getName()).thenReturn("color");
        when(def.getSelectableValues()).thenReturn(List.of("black", "white"));
        ClothesAttribute attr = mock(ClothesAttribute.class);
        when(attr.getDefinition()).thenReturn(def);
        when(attr.getValue()).thenReturn("black");
        Clothes first = clothes(a, "shirt", ClothesType.TOP, List.of(attr)), second = clothes(b, "pants", ClothesType.BOTTOM, List.of());
        when(feedClothesRepository.findByFeed_Id(id)).thenReturn(List.of(new FeedClothes(f, a), new FeedClothes(f, b), new FeedClothes(f, a), new FeedClothes(f, missing)));
        when(clothesRepository.findAllById(List.of(a, b, missing))).thenReturn(List.of(second, first));
        when(feedLikeRepository.existsByFeed_IdAndUserId(id, userId)).thenReturn(true);
        FeedResponse r = service.toResponse(f, userId);
        assertTrue(r.likedByMe());
        assertEquals("profile", r.author().profileImageUrl());
        assertEquals(new FeedResponse.WeatherDto(weatherId, "CLEAR", new FeedResponse.PrecipitationDto("NONE", 0.0, 10.0), new FeedResponse.TemperatureDto(15.0, 1.0, 8.0, 18.0)), r.weather());
        assertEquals(List.of(new FeedResponse.OotdDto(a, "shirt", "image", "TOP", List.of(new FeedResponse.AttributeDto(defId, "color", List.of("black", "white"), "black"))), new FeedResponse.OotdDto(b, "pants", "image", "BOTTOM", List.of())), r.ootds());
    }

    Clothes clothes(UUID id, String name, ClothesType type, List<ClothesAttribute> attrs) {
        Clothes c = mock(Clothes.class);
        when(c.getId()).thenReturn(id);
        when(c.getName()).thenReturn(name);
        when(c.getType()).thenReturn(type);
        when(c.getImageUrl()).thenReturn("image");
        when(c.getAttributes()).thenReturn(attrs);
        return c;
    }

    @Test
    void commentCountOverflow() {
        Feed f = setup(null);
        when(commentRepository.countByFeed_Id(id)).thenReturn((long) Integer.MAX_VALUE + 1);
        assertThrows(ArithmeticException.class, () -> service.toResponse(f));
    }

}
