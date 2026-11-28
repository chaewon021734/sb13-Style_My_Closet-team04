package com.project.sb13ootbsteam04.user.controller;

import com.project.sb13ootbsteam04.clothes.exception.InvalidClothesImageException;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;
import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;
import com.project.sb13ootbsteam04.user.dto.request.ProfileUpdateRequest;
import com.project.sb13ootbsteam04.user.dto.response.ProfileDto;
import com.project.sb13ootbsteam04.user.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/profiles")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;
  private final JwtUserIdResolver userIdResolver;

  @GetMapping
  public ResponseEntity<ProfileDto> get(@PathVariable UUID userId) {
    return ResponseEntity.ok(profileService.get(userId));
  }

  @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ProfileDto> update(
      Authentication authentication,
      @PathVariable UUID userId,
      @RequestPart("request") @Valid ProfileUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image
  ) {
    return ResponseEntity.ok(profileService.update(
        userIdResolver.resolve(authentication), userId, request, toImageCommand(image)));
  }

  // 의상 이미지와 같은 규칙(형식·크기·내용)으로 검증한다
  private ClothesImageCommand toImageCommand(MultipartFile image) {
    if (image == null || image.isEmpty()) {
      return null;
    }
    if (image.getSize() > ClothesImageCommand.MAX_SIZE_BYTES) {
      throw InvalidClothesImageException.tooLarge();
    }
    try {
      return new ClothesImageCommand(image.getOriginalFilename(), image.getContentType(), image.getBytes());
    } catch (IOException exception) {
      throw InvalidClothesImageException.readFailed(exception);
    }
  }
}
