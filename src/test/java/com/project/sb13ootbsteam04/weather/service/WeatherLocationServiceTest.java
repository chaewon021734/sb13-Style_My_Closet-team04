package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.client.KakaoLocationClient;
import com.project.sb13ootbsteam04.weather.service.condition.WeatherLocationCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeatherLocationServiceTest {

    private final KakaoLocationClient client = mock(KakaoLocationClient.class);
    private final WeatherLocationService service = new WeatherLocationService(client, new WeatherGridConverter());

    @Test
    void convertsSeoulCoordinatesAndReturnsAdministrativeNames() {
        when(client.getLocationNames(37.5665, 126.9780))
                .thenReturn(List.of("서울특별시", "중구", "명동"));

        var result = service.getLocation(new WeatherLocationCondition(37.5665, 126.9780));

        assertThat(result.latitude()).isEqualTo(37.5665);
        assertThat(result.longitude()).isEqualTo(126.9780);
        assertThat(result.x()).isEqualTo(60);
        assertThat(result.y()).isEqualTo(127);
        assertThat(result.locationNames()).containsExactly("서울특별시", "중구", "명동");
    }

    @Test
    void convertsJejuCoordinates() {
        var grid = new WeatherGridConverter().convert(33.4996, 126.5312);
        assertThat(grid.x()).isEqualTo(53);
        assertThat(grid.y()).isEqualTo(38);
    }

    @Test
    void rejectsInvalidCoordinatesBeforeCallingExternalApi() {
        for (double latitude : new double[]{-91, 91, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> new WeatherLocationCondition(latitude, 127))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (double longitude : new double[]{-181, 181, Double.NaN, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> new WeatherLocationCondition(37, longitude))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(client);
    }
}
