package com.project.sb13ootbsteam04.clothes.service;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefSearchCommand;
import java.util.List;

public interface ClothesAttributeDefService {

  ClothesAttributeDefResult create(ClothesAttributeDefCreateCommand command);

  List<ClothesAttributeDefResult> findAll(ClothesAttributeDefSearchCommand command);
}
