package com.project.sb13ootbsteam04.recommendation.service;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WindStrength;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.springframework.stereotype.Component;

@Component
public class OutfitRecommendationPolicy {

  static final int NEUTRAL_TEMPERATURE_SENSITIVITY = 3;
  private static final double SENSITIVITY_STEP_CELSIUS = 2.0;
  private static final double OUTER_TEMPERATURE_THRESHOLD = 20.0;
  private static final double COLD_ACCESSORY_THRESHOLD = 5.0;
  private static final double SOCKS_THRESHOLD = 12.0;

  public List<Clothes> recommend(
      Weather weather,
      int temperatureSensitivity,
      List<Clothes> wardrobe
  ) {
    Objects.requireNonNull(weather, "날씨는 필수입니다.");
    Objects.requireNonNull(wardrobe, "보유 의상 목록은 필수입니다.");
    validateTemperatureSensitivity(temperatureSensitivity);

    double perceivedTemperature = weather.getTemperatureCurrent()
        + (temperatureSensitivity - NEUTRAL_TEMPERATURE_SENSITIVITY)
        * SENSITIVITY_STEP_CELSIUS;
    int targetWarmth = targetWarmth(perceivedTemperature);
    Map<ClothesType, List<Clothes>> byType = groupByType(wardrobe);
    List<Clothes> result = new ArrayList<>();

    addBaseOutfit(result, byType, targetWarmth, weather);
    if (needsOuter(weather, perceivedTemperature)) {
      addBest(result, byType, ClothesType.OUTER, targetWarmth, weather);
    }
    addBest(result, byType, ClothesType.SHOES, targetWarmth, weather);

    if (perceivedTemperature <= SOCKS_THRESHOLD) {
      addBest(result, byType, ClothesType.SOCKS, targetWarmth, weather);
    }
    if (perceivedTemperature <= COLD_ACCESSORY_THRESHOLD) {
      addBest(result, byType, ClothesType.HAT, targetWarmth, weather);
      addBest(result, byType, ClothesType.SCARF, targetWarmth, weather);
      addBest(result, byType, ClothesType.ACCESSORY, targetWarmth, weather);
    }
    return List.copyOf(result);
  }

  private void addBaseOutfit(
      List<Clothes> result,
      Map<ClothesType, List<Clothes>> byType,
      int targetWarmth,
      Weather weather
  ) {
    boolean hasTopAndBottom = byType.containsKey(ClothesType.TOP)
        && byType.containsKey(ClothesType.BOTTOM);
    if (hasTopAndBottom || !byType.containsKey(ClothesType.DRESS)) {
      addBest(result, byType, ClothesType.TOP, targetWarmth, weather);
      addBest(result, byType, ClothesType.BOTTOM, targetWarmth, weather);
      return;
    }
    addBest(result, byType, ClothesType.DRESS, targetWarmth, weather);
  }

  private void addBest(
      List<Clothes> result,
      Map<ClothesType, List<Clothes>> byType,
      ClothesType type,
      int targetWarmth,
      Weather weather
  ) {
    byType.getOrDefault(type, List.of()).stream()
        .max(Comparator
            .comparingInt((Clothes clothes) -> score(clothes, targetWarmth, weather))
            .thenComparing(clothes -> clothes.getId().toString(), Comparator.reverseOrder()))
        .ifPresent(result::add);
  }

  private int score(Clothes clothes, int targetWarmth, Weather weather) {
    OptionalInt warmth = clothes.getAttributes().stream()
        .mapToInt(this::warmth)
        .filter(value -> value >= 0)
        .findFirst();
    int temperatureScore = warmth.isPresent()
        ? 20 - Math.abs(warmth.getAsInt() - targetWarmth) * 5
        : 0;
    int precipitationScore = isPrecipitating(weather)
        && hasWeatherproofAttribute(clothes) ? 5 : 0;
    return temperatureScore + precipitationScore;
  }

  private int warmth(ClothesAttribute attribute) {
    String definitionName = normalize(attribute.getDefinition().getName());
    if (!(definitionName.contains("계절")
        || definitionName.contains("season")
        || definitionName.contains("두께")
        || definitionName.contains("thickness")
        || definitionName.contains("보온")
        || definitionName.contains("warmth"))) {
      return -1;
    }

    String value = normalize(attribute.getValue());
    if (containsAny(value, "겨울", "winter", "매우두꺼", "verythick", "고보온", "high")) {
      return 4;
    }
    if (containsAny(value, "두꺼", "thick", "가을", "autumn", "fall")) {
      return 3;
    }
    if (containsAny(value, "봄", "spring", "보통", "medium")) {
      return 2;
    }
    if (containsAny(value, "여름", "summer", "매우얇", "verythin", "저보온", "low")) {
      return 0;
    }
    if (containsAny(value, "얇", "thin")) {
      return 1;
    }
    return -1;
  }

  private boolean hasWeatherproofAttribute(Clothes clothes) {
    return clothes.getAttributes().stream()
        .map(attribute -> normalize(
            attribute.getDefinition().getName() + " " + attribute.getValue()))
        .anyMatch(value -> containsAny(value, "방수", "생활방수", "waterproof", "water-resistant"));
  }

  private boolean needsOuter(Weather weather, double perceivedTemperature) {
    return perceivedTemperature <= OUTER_TEMPERATURE_THRESHOLD
        || isPrecipitating(weather)
        || weather.getWindSpeedAsWord() == WindStrength.STRONG;
  }

  private boolean isPrecipitating(Weather weather) {
    return weather.getPrecipitationType() != PrecipitationType.NONE;
  }

  private int targetWarmth(double perceivedTemperature) {
    if (perceivedTemperature <= 5.0) {
      return 4;
    }
    if (perceivedTemperature <= 12.0) {
      return 3;
    }
    if (perceivedTemperature <= 20.0) {
      return 2;
    }
    if (perceivedTemperature <= 27.0) {
      return 1;
    }
    return 0;
  }

  private Map<ClothesType, List<Clothes>> groupByType(List<Clothes> wardrobe) {
    Map<ClothesType, List<Clothes>> byType = new EnumMap<>(ClothesType.class);
    for (Clothes clothes : wardrobe) {
      byType.computeIfAbsent(clothes.getType(), ignored -> new ArrayList<>()).add(clothes);
    }
    return byType;
  }

  private void validateTemperatureSensitivity(int temperatureSensitivity) {
    if (temperatureSensitivity < 1 || temperatureSensitivity > 5) {
      throw new IllegalArgumentException("온도 민감도는 1 이상 5 이하여야 합니다.");
    }
  }

  private boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(normalize(candidate))) {
        return true;
      }
    }
    return false;
  }

  private String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "");
  }
}
