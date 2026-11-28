package com.project.sb13ootbsteam04.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// 보내지 않은(null) 값은 바꾸지 않는다
public record ProfileUpdateRequest(
    @Size(max = 20, message = "이름은 20자 이하여야 합니다.")
    @Pattern(regexp = "(?s).*\\S.*", message = "이름은 비어 있을 수 없습니다.")
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

  // 날씨 위치 조회 결과(WeatherAPILocation)를 그대로 저장한다.
  // 위치를 보낼 때는 모든 값이 있어야 한다(화면의 지명 표시와 날씨 알림의 격자 비교에 필요).
  public record Location(
      @NotNull Double latitude,
      @NotNull Double longitude,
      @NotNull Integer x,
      @NotNull Integer y,
      @NotEmpty List<String> locationNames
  ) {
  }
}
