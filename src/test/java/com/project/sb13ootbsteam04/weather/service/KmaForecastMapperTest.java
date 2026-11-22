package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import org.junit.jupiter.api.Test;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmaForecastMapperTest {

    private final KmaForecastMapper mapper = new KmaForecastMapper();
    private final WeatherLocation location = new WeatherLocation(37.5665, 126.978, 60, 127);

    @Test
    void preservesTheDateParsingCauseAndPublicErrorDetails() {
        var items = List.of(new KmaForecastItem("20260230", "1100", "20261109", "1400",
                "TMP", "12", 60, 127));

        assertThatThrownBy(() -> mapper.toForecasts(location, items))
                .isInstanceOfSatisfying(WeatherForecastException.class, error -> {
                    assertThat(error.getCause()).isInstanceOf(DateTimeParseException.class);
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.WEATHER_FORECAST_RESPONSE_INVALID);
                    assertThat(error.getDetails()).containsExactlyEntriesOf(
                            Map.of("reason", "예보 날짜 또는 시각이 올바르지 않습니다."));
                });
    }

    @Test
    void preservesTheNumberParsingCauseAndPublicErrorDetails() {
        var items = List.of(
                new KmaForecastItem("20261109", "1100", "20261109", "1400", "PCP", "강수없음", 60, 127),
                new KmaForecastItem("20261109", "1100", "20261109", "1400", "WSD", "invalid", 60, 127));

        assertThatThrownBy(() -> mapper.toForecasts(location, items))
                .isInstanceOfSatisfying(WeatherForecastException.class, error -> {
                    assertThat(error.getCause()).isInstanceOf(NumberFormatException.class);
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.WEATHER_FORECAST_RESPONSE_INVALID);
                    assertThat(error.getDetails()).containsExactlyEntriesOf(
                            Map.of("reason", "예보 항목이 숫자가 아닙니다. 항목: WSD"));
                });
    }
}
