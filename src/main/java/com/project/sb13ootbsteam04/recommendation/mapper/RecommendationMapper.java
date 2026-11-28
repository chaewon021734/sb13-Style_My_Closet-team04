package com.project.sb13ootbsteam04.recommendation.mapper;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeWithDefDto;
import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.recommendation.dto.OotdDto;
import org.springframework.stereotype.Component;

@Component
public class RecommendationMapper {

  public OotdDto toDto(Clothes clothes) {
    return new OotdDto(
        clothes.getId(),
        clothes.getName(),
        clothes.getImageUrl(),
        clothes.getType(),
        clothes.getAttributes().stream().map(this::toDto).toList()
    );
  }

  private ClothesAttributeWithDefDto toDto(ClothesAttribute attribute) {
    var definition = attribute.getDefinition();
    return new ClothesAttributeWithDefDto(
        definition.getId(),
        definition.getName(),
        definition.getSelectableValues(),
        attribute.getValue()
    );
  }
}
