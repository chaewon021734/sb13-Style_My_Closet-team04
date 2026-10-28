package com.project.sb13ootbsteam04.clothes.controller;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesDto;
import com.project.sb13ootbsteam04.clothes.exception.InvalidClothesImageException;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapper;
import com.project.sb13ootbsteam04.clothes.service.ClothesService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
