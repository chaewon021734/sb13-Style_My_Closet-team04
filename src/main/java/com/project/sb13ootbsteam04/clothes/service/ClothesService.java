package com.project.sb13ootbsteam04.clothes.service;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCursorResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesSearchCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesUpdateCommand;
import java.util.UUID;

public interface ClothesService {

  ClothesResult create(ClothesCreateCommand command);

  ClothesCursorResult search(ClothesSearchCommand command);

  ClothesResult update(UUID actorId, ClothesUpdateCommand command);

  void delete(UUID actorId, UUID clothesId);
}
