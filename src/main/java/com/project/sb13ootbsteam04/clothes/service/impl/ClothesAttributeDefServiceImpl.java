package com.project.sb13ootbsteam04.clothes.service.impl;

import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClothesAttributeDefServiceImpl implements ClothesAttributeDefService {

  private final ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Override
  public ClothesAttributeDefResult create(ClothesAttributeDefCreateCommand command) {
    throw new UnsupportedOperationException("의상 속성 정의 등록 기능 구현이 필요합니다");
  }
}
