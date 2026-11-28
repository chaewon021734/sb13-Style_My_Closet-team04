package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;
import com.project.sb13ootbsteam04.weather.controller.WeatherController;
import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocationName;
import com.project.sb13ootbsteam04.weather.event.WeatherChangeEventPublisher;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationNameRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherLocationRepository;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, WeatherController.class, WeatherGridConverter.class,
        WeatherForecastService.class, KmaForecastMapper.class, WeatherChangeDetector.class,
        WeatherQueryService.class})
class WeatherQueryIntegrationTest {

    @Autowired private WeatherController controller;
    @Autowired private WeatherLocationRepository locations;
    @Autowired private WeatherLocationNameRepository names;
    @Autowired private WeatherRepository weathers;
    @Autowired private WeatherForecastService forecastService;
    @MockitoBean private WeatherLocationService locationService;
    @MockitoBean private WeatherChangeEventPublisher changeEventPublisher;
    // 저장된 예보 조회만 검증하므로 외부 API 수집은 대체한다
    @MockitoBean private WeatherForecastInitializer forecastInitializer;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsTheLatestStoredIssueForTheGridWithOriginalWeatherIds() throws Exception {
        var oldLocation = locations.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127));
        forecastService.saveForecast(oldLocation.getId(), slot(oldLocation, "0800", "1200"));
        var location = locations.saveAndFlush(new WeatherLocation(37.567, 126.979, 60, 127));
        names.save(new WeatherLocationName(location.getId(), 1, "중구"));
        names.save(new WeatherLocationName(location.getId(), 0, "서울특별시"));
        var items = new ArrayList<>(slot(location, "1100", "1500"));
        items.addAll(slot(location, "1100", "1400"));
        forecastService.saveForecast(location.getId(), items);
        var otherGrid = locations.saveAndFlush(new WeatherLocation(33.4996, 126.5312, 53, 38));
        forecastService.saveForecast(otherGrid.getId(), slot(otherGrid, "1400", "1600"));

        var saved = weathers.findAllByLocationIdAndForecastedAtOrderByForecastAtAsc(
                location.getId(), Instant.parse("2026-11-09T02:00:00Z"));

        mvc.perform(get("/api/weathers").param("latitude", "37.5665").param("longitude", "126.978"))
                .andExpect(status().isOk())
                // 같은 날짜의 시간별 예보는 가장 이른 예보 하나로 응답한다
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(saved.get(0).getId().toString()))
                .andExpect(jsonPath("$[0].forecastedAt").value("2026-11-09T02:00:00Z"))
                .andExpect(jsonPath("$[0].forecastAt").value("2026-11-09T05:00:00Z"))
                .andExpect(jsonPath("$[0].location.latitude").value(37.567))
                .andExpect(jsonPath("$[0].location.longitude").value(126.979))
                .andExpect(jsonPath("$[0].location.x").value(60))
                .andExpect(jsonPath("$[0].location.y").value(127))
                .andExpect(jsonPath("$[0].location.locationNames[0]").value("서울특별시"))
                .andExpect(jsonPath("$[0].location.locationNames[1]").value("중구"))
                .andExpect(jsonPath("$[0].skyStatus").value("MOSTLY_CLOUDY"))
                .andExpect(jsonPath("$[0].precipitation.type").value("NONE"))
                .andExpect(jsonPath("$[0].precipitation.amount").value(0.0))
                .andExpect(jsonPath("$[0].precipitation.probability").value(0.2))
                .andExpect(jsonPath("$[0].humidity.current").value(60.0))
                .andExpect(jsonPath("$[0].temperature.current").value(12.0))
                // 최저·최고 예보가 없으면 그날 시간별 기온 범위로 채운다
                .andExpect(jsonPath("$[0].temperature.min").value(12.0))
                .andExpect(jsonPath("$[0].temperature.max").value(12.0))
                .andExpect(jsonPath("$[0].windSpeed.speed").value(1.2))
                .andExpect(jsonPath("$[0].windSpeed.asWord").value("WEAK"));
        verifyNoInteractions(locationService);
    }

    @Test
    void returnsOneForecastPerDayWithDailyTemperatureRange() throws Exception {
        var location = locations.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127));
        names.save(new WeatherLocationName(location.getId(), 0, "서울특별시"));
        var items = new ArrayList<KmaForecastItem>();
        items.addAll(slot(location, "20261109", "0300", "5", Map.of()));
        items.addAll(slot(location, "20261109", "1500", "13", Map.of("TMX", "15.0")));
        items.addAll(slot(location, "20261110", "0600", "2", Map.of("TMN", "3.0")));
        items.addAll(slot(location, "20261110", "1100", "9", Map.of()));
        items.addAll(slot(location, "20261110", "1300", "11", Map.of()));
        items.addAll(slot(location, "20261110", "1500", "13", Map.of("TMX", "14.0")));
        // 예보 범위 끝의 자정 자료만 있는 날은 하루 칸으로 쓰지 않는다
        items.addAll(slot(location, "20261111", "0000", "7", Map.of()));
        forecastService.saveForecast(location.getId(), items);

        mvc.perform(get("/api/weathers").param("latitude", "37.5665").param("longitude", "126.978"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // 첫날은 가장 가까운 예보, 이후 날짜는 정오에 가장 가까운 예보
                .andExpect(jsonPath("$[0].forecastAt").value("2026-11-08T18:00:00Z"))
                .andExpect(jsonPath("$[0].temperature.current").value(5.0))
                .andExpect(jsonPath("$[0].temperature.min").value(5.0))
                .andExpect(jsonPath("$[0].temperature.max").value(15.0))
                .andExpect(jsonPath("$[1].forecastAt").value("2026-11-10T02:00:00Z"))
                .andExpect(jsonPath("$[1].temperature.current").value(9.0))
                .andExpect(jsonPath("$[1].temperature.min").value(3.0))
                .andExpect(jsonPath("$[1].temperature.max").value(14.0));
    }

    @Test
    void returnsAnEmptyArrayWhenTheGridHasNoForecasts() throws Exception {
        locations.saveAndFlush(new WeatherLocation(37.5665, 126.978, 60, 127));

        mvc.perform(get("/api/weathers").param("latitude", "37.5665").param("longitude", "126.978"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsMissingAndInvalidCoordinates() throws Exception {
        mvc.perform(get("/api/weathers").param("latitude", "37.5665"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").exists());
        for (String latitude : List.of("abc", "91", "NaN", "Infinity")) {
            mvc.perform(get("/api/weathers").param("latitude", latitude).param("longitude", "126.978"))
                    .andExpect(status().isBadRequest());
        }
    }

    private List<KmaForecastItem> slot(WeatherLocation location, String forecastDate, String forecastTime,
                                       String temperature, Map<String, String> extra) {
        var values = new java.util.HashMap<>(Map.of("SKY", "1", "PTY", "0", "PCP", "강수없음", "POP", "0",
                "REH", "50", "TMP", temperature, "WSD", "1.0"));
        values.putAll(extra);
        return values.entrySet().stream().map(entry -> new KmaForecastItem("20261109", "0200", forecastDate,
                        forecastTime, entry.getKey(), entry.getValue(), location.getGridX(), location.getGridY()))
                .toList();
    }

    private List<KmaForecastItem> slot(WeatherLocation location, String issue, String forecastTime) {
        return Map.of("SKY", "3", "PTY", "0", "PCP", "강수없음", "POP", "20", "REH", "60", "TMP", "12", "WSD", "1.2")
                .entrySet().stream().map(entry -> new KmaForecastItem("20261109", issue, "20261109", forecastTime,
                        entry.getKey(), entry.getValue(), location.getGridX(), location.getGridY()))
                .toList();
    }
}
