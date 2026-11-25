package com.project.sb13ootbsteam04.clothes.controller;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesDto;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesUpdateRequest;
import com.project.sb13ootbsteam04.clothes.exception.InvalidClothesImageException;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapper;
import com.project.sb13ootbsteam04.clothes.service.ClothesService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesUpdateCommand;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes")
public class ClothesController implements ClothesApi {

  private final ClothesService clothesService;
  private final ClothesMapper clothesMapper;

  @Override
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ClothesDto> create(
      @Valid @RequestPart("request") ClothesCreateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image
  ) {

    ClothesImageCommand imageCommand = toImageCommand(image);
    ClothesCreateCommand requestCommand = clothesMapper.toCommand(request);
    ClothesCreateCommand command = new ClothesCreateCommand(
        requestCommand.ownerId(),
        requestCommand.name(),
        requestCommand.type(),
        requestCommand.attributes(),
        imageCommand
    );
    ClothesResult result = clothesService.create(command);
    ClothesDto response = clothesMapper.toDto(result);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Override
  @PatchMapping(value = "/{clothesId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ClothesDto> update(
      @PathVariable UUID clothesId,
      @Valid @RequestPart("request") ClothesUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image) {

    // 경로 ID와 두 multipart 파트를 하나의 수정 명령으로 전달한다.
    ClothesUpdateCommand command = clothesMapper.toCommand(clothesId, request, toImageCommand(image));
    ClothesResult result = clothesService.update(command);
    ClothesDto response = clothesMapper.toDto(result);

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  private ClothesImageCommand toImageCommand(MultipartFile image) {

    if (image == null || image.isEmpty()) {
      return null;
    }

    if (image.getSize() > ClothesImageCommand.MAX_SIZE_BYTES) {
      throw InvalidClothesImageException.tooLarge();
    }

    try {
      return new ClothesImageCommand(
          image.getOriginalFilename(),
          image.getContentType(),
          image.getBytes()
      );

    } catch (IOException exception) {
      throw InvalidClothesImageException.readFailed(exception);
    }
  }
}
