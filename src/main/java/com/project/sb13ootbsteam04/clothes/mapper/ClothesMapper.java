package com.project.sb13ootbsteam04.clothes.mapper;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeCommand;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDto;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeWithDefDto;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClothesMapper {

  @Mapping(target = "image", ignore = true)
  ClothesCreateCommand toCommand(ClothesCreateRequest request);

  ClothesAttributeCommand toCommand(ClothesAttributeDto attribute);

  // User 연관관계 적용 후 @Mapping(target = "ownerId", source = "owner.id")를 복원한다.
  ClothesDto toDto(Clothes clothes);

  @Mapping(target = "definitionId", source = "definition.id")
  @Mapping(target = "definitionName", source = "definition.name")
  @Mapping(target = "selectableValues", source = "definition.selectableValues")
  ClothesAttributeWithDefDto toDto(ClothesAttribute attribute);
}
