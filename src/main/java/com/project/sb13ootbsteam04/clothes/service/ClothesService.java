package com.project.sb13ootbsteam04.clothes.service;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesUpdateCommand;

public interface ClothesService {

  ClothesResult create(ClothesCreateCommand command);

  ClothesResult update(ClothesUpdateCommand command);
}
