package com.project.sb13ootbsteam04.clothes.service;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesDto;

public interface ClothesService {

  ClothesDto create(ClothesCreateCommand command);
}
