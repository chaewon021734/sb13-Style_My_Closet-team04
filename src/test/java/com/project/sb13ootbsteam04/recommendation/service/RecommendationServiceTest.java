package com.project.sb13ootbsteam04.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.recommendation.exception.RecommendationException;
import com.project.sb13ootbsteam04.recommendation.mapper.RecommendationMapper;
import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

  @Mock WeatherRepository weatherRepository;
  @Mock ProfileRepository profileRepository;
  @Mock ClothesRepository clothesRepository;
  @Mock OutfitRecommendationPolicy policy;

  private RecommendationService service;

  @BeforeEach
  void setUp() {
    service = new RecommendationService(
        weatherRepository,
        profileRepository,
        clothesRepository,
        policy,
        new RecommendationMapper()
    );
  }

  @Test
  void returnsSwaggerRecommendationShapeForUserWardrobe() {
    UUID weatherId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Weather weather = mock(Weather.class);
    Profile profile = mock(Profile.class);
    Clothes top = mock(Clothes.class);

    when(weatherRepository.findById(weatherId)).thenReturn(Optional.of(weather));
    when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
    when(profile.getTemperatureSensitivity()).thenReturn(1);
    when(clothesRepository.findAllWithAttributesByOwnerId(userId)).thenReturn(List.of(top));
    when(policy.recommend(weather, 1, List.of(top))).thenReturn(List.of(top));
    when(top.getId()).thenReturn(UUID.randomUUID());
    when(top.getName()).thenReturn("겨울 코트");
    when(top.getImageUrl()).thenReturn("https://example.com/coat.png");
    when(top.getType()).thenReturn(ClothesType.OUTER);
    when(top.getAttributes()).thenReturn(List.of());

    var result = service.getRecommendation(weatherId, userId);

    assertThat(result.weatherId()).isEqualTo(weatherId);
    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.clothes()).singleElement().satisfies(item -> {
      assertThat(item.clothesId()).isEqualTo(top.getId());
      assertThat(item.name()).isEqualTo("겨울 코트");
      assertThat(item.type()).isEqualTo(ClothesType.OUTER);
    });
  }

  @Test
  void usesNeutralSensitivityWhenProfileOrValueIsMissing() {
    UUID weatherId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Weather weather = mock(Weather.class);
    when(weatherRepository.findById(weatherId)).thenReturn(Optional.of(weather));
    when(profileRepository.findById(userId)).thenReturn(Optional.empty());
    when(clothesRepository.findAllWithAttributesByOwnerId(userId)).thenReturn(List.of());
    when(policy.recommend(weather, 3, List.of())).thenReturn(List.of());

    service.getRecommendation(weatherId, userId);

    verify(policy).recommend(weather, 3, List.of());
  }

  @Test
  void rejectsUnknownWeatherBeforeReadingProfileOrWardrobe() {
    UUID weatherId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(weatherRepository.findById(weatherId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getRecommendation(weatherId, userId))
        .isInstanceOf(RecommendationException.class)
        .hasMessageContaining("날씨");
  }
}
