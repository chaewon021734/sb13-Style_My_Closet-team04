package com.project.sb13ootbsteam04.clothes.controller;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefDto;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.http.ResponseEntity;

public interface ClothesAttributeDefApi {

  ResponseEntity<ClothesAttributeDefDto> create(
      @RequestBody(
          required = true,
          content = @Content(schema = @Schema(implementation = ClothesAttributeDefCreateRequest.class)))
      ClothesAttributeDefCreateRequest request
  );
}
