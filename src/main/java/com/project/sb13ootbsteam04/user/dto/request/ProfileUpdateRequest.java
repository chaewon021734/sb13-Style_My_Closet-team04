package com.project.sb13ootbsteam04.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// 보내지 않은(null) 값은 바꾸지 않는다
public record ProfileUpdateRequest(
    @Size(max = 20, message = "이름은 20자 이하여야 합니다.")
    @Pattern(regexp = ".*\\S.*", message = "이름은 비어 있을 수 없습니다.")
    String name,
    Gender gender,
    LocalDate birthDate,
    @Valid Location location,
    @Min(value = 1, message = "온도 민감도는 1 이상이어야 합니다.")
    @Max(value = 5, message = "온도 민감도는 5 이하여야 합니다.")
    Integer temperatureSensitivity
) {

  public enum Gender {
    MALE, FEMALE, OTHER
  }

  // 날씨 위치 조회 결과(WeatherAPILocation)를 그대로 저장한다
  public record Location(
      Double latitude,
      Double longitude,
      Integer x,
      Integer y,
      List<String> locationNames
  ) {
  }
}
