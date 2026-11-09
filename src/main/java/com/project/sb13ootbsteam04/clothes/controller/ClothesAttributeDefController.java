package com.project.sb13ootbsteam04.clothes.controller;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefDto;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefUpdateRequest;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesAttributeDefMapper;
import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefUpdateCommand;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes/attribute-defs")
public class ClothesAttributeDefController implements ClothesAttributeDefApi {

  private final ClothesAttributeDefService clothesAttributeDefService;
  private final ClothesAttributeDefMapper clothesAttributeDefMapper;

  @Override
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ClothesAttributeDefDto> create(
      @Valid @RequestBody ClothesAttributeDefCreateRequest request) {
    // Request DTO는 Mapper를 통해 Service Command로 한 번만 변환한다.
    ClothesAttributeDefCreateCommand command = clothesAttributeDefMapper.toCommand(request);
    ClothesAttributeDefResult result = clothesAttributeDefService.create(command);
    ClothesAttributeDefDto response = clothesAttributeDefMapper.toDto(result);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Override
  @PatchMapping(value = "/{definitionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ClothesAttributeDefDto> update(
      @PathVariable UUID definitionId,
      @Valid @RequestBody ClothesAttributeDefUpdateRequest request) {
    // 경로 ID와 요청 본문을 서비스 입력 하나로 결합한다.
    ClothesAttributeDefUpdateCommand command = clothesAttributeDefMapper.toCommand(definitionId, request);
    ClothesAttributeDefResult result = clothesAttributeDefService.update(command);
    ClothesAttributeDefDto response = clothesAttributeDefMapper.toDto(result);

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }
}
