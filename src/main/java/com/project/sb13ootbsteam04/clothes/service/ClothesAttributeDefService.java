package com.project.sb13ootbsteam04.clothes.service;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;

public interface ClothesAttributeDefService {

  ClothesAttributeDefResult create(ClothesAttributeDefCreateCommand command);
}
