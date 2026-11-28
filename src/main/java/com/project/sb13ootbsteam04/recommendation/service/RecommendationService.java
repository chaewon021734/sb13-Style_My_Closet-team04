package com.project.sb13ootbsteam04.recommendation.service;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.recommendation.dto.OotdDto;
import com.project.sb13ootbsteam04.recommendation.dto.RecommendationDto;
import com.project.sb13ootbsteam04.recommendation.exception.RecommendationException;
import com.project.sb13ootbsteam04.recommendation.mapper.RecommendationMapper;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

  private final WeatherRepository weatherRepository;
  private final ProfileRepository profileRepository;
  private final ClothesRepository clothesRepository;
  private final OutfitRecommendationPolicy policy;
  private final RecommendationMapper mapper;

  @Transactional(readOnly = true)
  public RecommendationDto getRecommendation(UUID weatherId, UUID userId) {
    var weather = weatherRepository.findById(weatherId)
        .orElseThrow(() -> RecommendationException.weatherNotFound(weatherId));
    int sensitivity = profileRepository.findById(userId)
        .map(profile -> profile.getTemperatureSensitivity() == null
            ? OutfitRecommendationPolicy.NEUTRAL_TEMPERATURE_SENSITIVITY
            : profile.getTemperatureSensitivity())
        .orElse(OutfitRecommendationPolicy.NEUTRAL_TEMPERATURE_SENSITIVITY);
    List<Clothes> wardrobe = clothesRepository.findAllWithAttributesByOwnerId(userId);
    List<OotdDto> clothes = policy.recommend(weather, sensitivity, wardrobe).stream()
        .map(mapper::toDto)
        .toList();
    return new RecommendationDto(weatherId, userId, clothes);
  }
}
