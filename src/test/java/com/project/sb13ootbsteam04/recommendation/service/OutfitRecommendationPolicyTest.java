package com.project.sb13ootbsteam04.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WindStrength;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutfitRecommendationPolicyTest {

  private final OutfitRecommendationPolicy policy = new OutfitRecommendationPolicy();

  @Test
  void temperatureSensitivityChangesWhetherOuterIsNeeded() {
    Weather weather = weather(23.0, PrecipitationType.NONE, WindStrength.WEAK);
    Clothes top = clothes("top", ClothesType.TOP);
    Clothes bottom = clothes("bottom", ClothesType.BOTTOM);
    Clothes outer = clothes("outer", ClothesType.OUTER);
    Clothes shoes = clothes("shoes", ClothesType.SHOES);
    List<Clothes> wardrobe = List.of(top, bottom, outer, shoes);

    assertThat(policy.recommend(weather, 1, wardrobe)).contains(outer);
    assertThat(policy.recommend(weather, 5, wardrobe)).doesNotContain(outer);
  }

  @Test
  void precipitationAddsOuterEvenInHotWeather() {
    Weather weather = weather(30.0, PrecipitationType.RAIN, WindStrength.WEAK);
    Clothes outer = clothes("rain outer", ClothesType.OUTER);

    assertThat(policy.recommend(weather, 3, List.of(
        clothes("top", ClothesType.TOP),
        clothes("bottom", ClothesType.BOTTOM),
        outer
    ))).contains(outer);
  }

  @Test
  void strongWindAddsOuterEvenInMildWeather() {
    Weather weather = weather(24.0, PrecipitationType.NONE, WindStrength.STRONG);
    Clothes outer = clothes("wind outer", ClothesType.OUTER);

    assertThat(policy.recommend(weather, 3, List.of(
        clothes("top", ClothesType.TOP),
        clothes("bottom", ClothesType.BOTTOM),
        outer
    ))).contains(outer);
  }

  @Test
  void choosesSeasonAttributeClosestToWeather() {
    Weather weather = weather(4.0, PrecipitationType.NONE, WindStrength.WEAK);
    Clothes summerTop = clothesWithAttribute("summer top", ClothesType.TOP, "계절", "여름");
    Clothes winterTop = clothesWithAttribute("winter top", ClothesType.TOP, "계절", "겨울");

    assertThat(policy.recommend(weather, 3, List.of(
        summerTop,
        winterTop,
        clothes("bottom", ClothesType.BOTTOM)
    ))).contains(winterTop).doesNotContain(summerTop);
  }

  @Test
  void returnsOneItemPerOutfitTypeInStableOrder() {
    Weather weather = weather(10.0, PrecipitationType.NONE, WindStrength.WEAK);

    assertThat(policy.recommend(weather, 3, List.of(
        clothes("shoes", ClothesType.SHOES),
        clothes("outer", ClothesType.OUTER),
        clothes("bottom", ClothesType.BOTTOM),
        clothes("top", ClothesType.TOP),
        clothes("another top", ClothesType.TOP)
    ))).extracting(Clothes::getType)
        .containsExactly(ClothesType.TOP, ClothesType.BOTTOM, ClothesType.OUTER, ClothesType.SHOES);
  }

  private Weather weather(
      double temperature,
      PrecipitationType precipitationType,
      WindStrength windStrength
  ) {
    Weather weather = mock(Weather.class);
    when(weather.getTemperatureCurrent()).thenReturn(temperature);
    when(weather.getPrecipitationType()).thenReturn(precipitationType);
    when(weather.getWindSpeedAsWord()).thenReturn(windStrength);
    return weather;
  }

  private Clothes clothes(String name, ClothesType type) {
    Clothes clothes = mock(Clothes.class);
    when(clothes.getId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
    when(clothes.getName()).thenReturn(name);
    when(clothes.getType()).thenReturn(type);
    when(clothes.getAttributes()).thenReturn(List.of());
    return clothes;
  }

  private Clothes clothesWithAttribute(
      String name,
      ClothesType type,
      String definitionName,
      String value
  ) {
    Clothes clothes = clothes(name, type);
    ClothesAttribute attribute = mock(ClothesAttribute.class);
    ClothesAttributeDef definition = mock(ClothesAttributeDef.class);
    when(definition.getName()).thenReturn(definitionName);
    when(attribute.getDefinition()).thenReturn(definition);
    when(attribute.getValue()).thenReturn(value);
    when(clothes.getAttributes()).thenReturn(List.of(attribute));
    return clothes;
  }
}
