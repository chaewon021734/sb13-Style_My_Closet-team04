package com.project.sb13ootbsteam04.clothes.service;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefUpdateCommand;

public interface ClothesAttributeDefService {

  ClothesAttributeDefResult create(ClothesAttributeDefCreateCommand command);

  ClothesAttributeDefResult update(ClothesAttributeDefUpdateCommand command);
}
