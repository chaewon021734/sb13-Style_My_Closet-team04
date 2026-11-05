package com.project.sb13ootbsteam04.clothes.controller;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefDto;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesAttributeDefMapper;
import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefSearchCommand;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping
  @Override
  public ResponseEntity<List<ClothesAttributeDefDto>> findAll(
      @RequestParam(name = "sortBy") String sortBy,
      @RequestParam(name = "sortDirection") String sortDirection,
      @RequestParam(name = "keywordLike", required = false) String keywordLike) {

    Direction direction = switch (sortDirection) {
      case "ASCENDING" -> Direction.ASC;
      case "DESCENDING" -> Direction.DESC;
      default -> throw new IllegalArgumentException("지원하지 않는 정렬 방향입니다: " + sortDirection);
    };

    // API 문자열을 검증된 서비스 조회 조건으로 변환한다.
    ClothesAttributeDefSearchCommand searchCommand = new ClothesAttributeDefSearchCommand(sortBy, direction, keywordLike);
    List<ClothesAttributeDefResult> results = clothesAttributeDefService.findAll(searchCommand);
    List<ClothesAttributeDefDto> response = results.stream()
        .map(clothesAttributeDefMapper::toDto)
        .toList();

    return ResponseEntity.ok(response);
  }
}
