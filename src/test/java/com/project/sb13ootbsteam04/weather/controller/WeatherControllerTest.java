package com.project.sb13ootbsteam04.weather.controller;

import com.project.sb13ootbsteam04.weather.dto.WeatherLocationResponse;
import com.project.sb13ootbsteam04.weather.exception.WeatherLocationException;
import com.project.sb13ootbsteam04.weather.service.WeatherLocationService;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WeatherControllerTest {

    private final WeatherLocationService service = mock(WeatherLocationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WeatherController(service))
                .setControllerAdvice(new WeatherExceptionHandler()).build();
    }

    @Test
    void returnsSwaggerLocationResponse() throws Exception {
        when(service.getLocation(new WeatherLocationCondition(37.5665, 126.978)))
                .thenReturn(new WeatherLocationResponse(37.5665, 126.978, 60, 127,
                        List.of("서울특별시", "중구", "명동")));
        mvc.perform(get("/api/weathers/location").param("longitude", "126.978").param("latitude", "37.5665"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(37.5665))
                .andExpect(jsonPath("$.longitude").value(126.978))
                .andExpect(jsonPath("$.x").value(60))
                .andExpect(jsonPath("$.y").value(127))
                .andExpect(jsonPath("$.locationNames[0]").value("서울특별시"));
    }

    @Test
    void rejectsMissingOrMalformedCoordinates() throws Exception {
        mvc.perform(get("/api/weathers/location").param("latitude", "37"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.exceptionName").exists());
        mvc.perform(get("/api/weathers/location").param("longitude", "abc").param("latitude", "37"))
                .andExpect(status().isBadRequest());
        for (String latitude : new String[]{"91", "NaN", "Infinity"}) {
            mvc.perform(get("/api/weathers/location").param("longitude", "127").param("latitude", latitude))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").isMap());
        }
        verifyNoInteractions(service);
    }

    @Test
    void returnsSwaggerErrorWhenRegionCannotBeResolved() throws Exception {
        when(service.getLocation(any())).thenThrow(new WeatherLocationException("행정구역 정보를 조회할 수 없습니다."));
        mvc.perform(get("/api/weathers/location").param("longitude", "127").param("latitude", "37"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("WeatherLocationException"))
                .andExpect(jsonPath("$.details.errorCode").value("WEATHER_LOCATION_LOOKUP_FAILED"))
                .andExpect(jsonPath("$.message").value("행정구역 정보를 조회할 수 없습니다."))
                .andExpect(jsonPath("$.details").isMap());
    }
}
