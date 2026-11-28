package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.dto.*;
import com.project.sb13ootbsteam04.feed.entity.*;
import com.project.sb13ootbsteam04.feed.repository.*;
import com.project.sb13ootbsteam04.clothes.entity.*;
import com.project.sb13ootbsteam04.clothes.repository.*;
import com.project.sb13ootbsteam04.user.repository.*;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.weather.entity.*;
import com.project.sb13ootbsteam04.weather.repository.*;
import org.springframework.data.domain.PageRequest;

import java.util.*;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceAdditionalTest {
    @Mock
    FeedRepository feedRepository;
    @Mock
    FeedClothesRepository feedClothesRepository;
    @Mock
    FeedLikeRepository feedLikeRepository;
    @Mock
    CommentRepository commentRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    FeedResponseService feedResponseService;
    @Mock
    ClothesRepository clothesRepository;
    @Mock
    WeatherRepository weatherRepository;
    @InjectMocks
    FeedService service;
    UUID user = UUID.randomUUID(), weather = UUID.randomUUID(), id = UUID.randomUUID(), clothes = UUID.randomUUID();
    Instant time = Instant.parse("2026-11-24T00:00:00Z");

    @Test
    void missingAuthor() {
        assertThrows(IllegalArgumentException.class, () -> service.createFeed(new CreateFeedRequest(null, weather, List.of(), "post"), user));
    }

    @Test
    void nullClothes() {
        when(weatherRepository.existsById(weather)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createFeed(new CreateFeedRequest(user, weather, null, "post"), user));
        verifyNoInteractions(feedRepository);
    }

    @Test
    void nullClothesElement() {
        when(weatherRepository.existsById(weather)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createFeed(new CreateFeedRequest(user, weather, Arrays.asList(clothes, null), "post"), user));
        verifyNoInteractions(feedRepository);
    }

    @Test
    void missingClothes() {
        when(weatherRepository.existsById(weather)).thenReturn(true);
        when(userRepository.existsById(user)).thenReturn(true);
        when(clothesRepository.findAllById(List.of(clothes))).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.createFeed(new CreateFeedRequest(user, weather, List.of(clothes), "post"), user));
        verifyNoInteractions(feedRepository);
    }

    @Test
    void createDeduplicatesClothes() {
        when(weatherRepository.existsById(weather)).thenReturn(true);
        when(userRepository.existsById(user)).thenReturn(true);
        Clothes ownedClothes = mock(Clothes.class);
        User owner = mock(User.class);
        when(ownedClothes.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(user);
        when(clothesRepository.findAllById(List.of(clothes))).thenReturn(List.of(ownedClothes));
        Feed saved = mock(Feed.class);
        when(saved.getId()).thenReturn(id);
        when(feedRepository.save(any(Feed.class))).thenReturn(saved);
        assertEquals(id, service.createFeed(new CreateFeedRequest(user, weather, List.of(clothes, clothes), "post"), user));
        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUserId());
        assertEquals(weather, captor.getValue().getWeatherId());
        assertEquals("post", captor.getValue().getContent());
        verify(feedClothesRepository).saveAll(argThat(items -> {
            List<FeedClothes> list = new ArrayList<>();
            items.forEach(list::add);
            return list.size() == 1 && list.get(0).getFeed() == saved && clothes.equals(list.get(0).getClothesId());
        }));
    }

    @Test
    void updateNullContent() {
        invalidUpdate(null);
    }

    @Test
    void updateBlankContent() {
        invalidUpdate("  ");
    }

    void invalidUpdate(String content) {
        Feed f = new Feed(user, weather, "old");
        when(feedRepository.findById(id)).thenReturn(Optional.of(f));
        assertThrows(IllegalArgumentException.class, () -> service.updateFeed(id, new UpdateFeedRequest(content), user));
        assertEquals("old", f.getContent());
    }

    @Test
    void createdAtCursorAndFilters() {
        Feed f = mock(Feed.class);
        when(f.getId()).thenReturn(id);
        when(f.getCreatedAt()).thenReturn(time);
        Feed extra = mock(Feed.class);
        FeedResponse dto = mock(FeedResponse.class);
        when(feedRepository.findFilteredPageDescending(user, "shirt", SkyStatus.CLEAR, PrecipitationType.NONE, null, null, PageRequest.of(0, 2))).thenReturn(List.of(f, extra));
        when(feedRepository.countFilteredFeeds(user, "shirt", SkyStatus.CLEAR, PrecipitationType.NONE)).thenReturn(2L);
        when(feedResponseService.toResponse(f, user)).thenReturn(dto);
        FeedPageResponse r = service.getFeedPage(null, null, 1, "createdAt", "DESCENDING", " shirt ", " CLEAR ", " NONE ", user, user);
        assertEquals(List.of(dto), r.data());
        assertTrue(r.hasNext());
        assertEquals(time.toString(), r.nextCursor());
        assertEquals(id, r.nextIdAfter());
        assertEquals(2L, r.totalCount());
        verifyNoInteractions(extra);
    }

    @Test
    void likeCountCursor() {
        Feed f = mock(Feed.class);
        when(f.getId()).thenReturn(id);
        when(f.getCreatedAt()).thenReturn(time);
        FeedResponse dto = mock(FeedResponse.class);
        when(feedRepository.findByLikeCountDescending(null, null, null, null, PageRequest.of(0, 2))).thenReturn(List.of(f, mock(Feed.class)));
        when(feedLikeRepository.countByFeed_Id(id)).thenReturn(7L);
        when(feedResponseService.toResponse(f, user)).thenReturn(dto);
        FeedPageResponse r = service.getFeedPage(null, null, 1, "likeCount", "DESCENDING", null, null, user);
        assertEquals("7|" + time, r.nextCursor());
        assertEquals(id, r.nextIdAfter());
        assertTrue(r.hasNext());
        assertEquals(List.of(dto), r.data());
    }

    @Test
    void blankFiltersAndCursor() {
        when(feedRepository.findFilteredPageAscending(null, null, null, null, null, null, PageRequest.of(0, 11))).thenReturn(List.of());
        assertFalse(service.getFeedPage(" ", null, 10, "createdAt", "ASCENDING", " ", " ", " ", null, user).hasNext());
    }

    @Test
    void malformedLikeCursor() {
        assertThrows(IllegalArgumentException.class, () -> service.getFeedPage("abc|" + time, id, 10, "likeCount", "DESCENDING", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getFeedPage("1|bad", id, 10, "likeCount", "DESCENDING", null, null));
    }

    @Test
    void legacySingleResponse() {
        Feed f = new Feed(user, weather, "post");
        FeedResponse dto = mock(FeedResponse.class);
        when(feedRepository.findById(id)).thenReturn(Optional.of(f));
        when(feedResponseService.toResponse(f, null)).thenReturn(dto);
        assertSame(dto, service.getFeedResponse(id));
    }

    @Test
    void entityValidation() {
        Feed f = new Feed(user, weather, "post");
        assertThrows(IllegalArgumentException.class, () -> new FeedClothes(null, clothes));
        assertThrows(IllegalArgumentException.class, () -> new FeedClothes(f, null));
        Comment c = new Comment(f, user, "old");
        c.updateContent("new");
        assertEquals("new", c.getContent());
    }

}
