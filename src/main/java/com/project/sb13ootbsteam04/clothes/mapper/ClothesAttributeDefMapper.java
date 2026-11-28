package com.project.sb13ootbsteam04.clothes.mapper;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefDto;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefUpdateRequest;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefUpdateCommand;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClothesAttributeDefMapper {

  ClothesAttributeDefCreateCommand toCommand(ClothesAttributeDefCreateRequest request);

  ClothesAttributeDefDto toDto(ClothesAttributeDefResult result);

  ClothesAttributeDefUpdateCommand toCommand(UUID definitionId, ClothesAttributeDefUpdateRequest request);
}
