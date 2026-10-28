package com.project.sb13ootbsteam04.clothes.service;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;

public interface ClothesService {

  ClothesResult create(ClothesCreateCommand command);
}
