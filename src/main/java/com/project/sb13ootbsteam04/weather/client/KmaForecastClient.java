package com.project.sb13ootbsteam04.weather.client;

import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.project.sb13ootbsteam04.global.exception.ErrorCode.WEATHER_FORECAST_API_KEY_NOT_CONFIGURED;
import static com.project.sb13ootbsteam04.global.exception.ErrorCode.WEATHER_FORECAST_LOOKUP_FAILED;
import static com.project.sb13ootbsteam04.global.exception.ErrorCode.WEATHER_FORECAST_RESPONSE_INVALID;
import static com.project.sb13ootbsteam04.global.exception.ErrorCode.WEATHER_FORECAST_SERVICE_UNAVAILABLE;

@Component
@Slf4j
public class KmaForecastClient {

    private static final String FORECAST_PATH =
            "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final int PAGE_NUMBER = 1;
    private static final int ROWS_PER_PAGE = 1000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    private final RestClient restClient;
    private final String apiKey;

    public KmaForecastClient(@Qualifier("kmaRestClient") RestClient restClient,
                             @Value("${weather.kma.api-key:}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public List<KmaForecastItem> getVillageForecast(
            LocalDate baseDate, LocalTime baseTime, int gridX, int gridY) {
        if (apiKey.isBlank()) {
            throw new WeatherForecastException(WEATHER_FORECAST_API_KEY_NOT_CONFIGURED,
                    "기상청 서비스 키 누락");
        }

        ForecastApiResponse apiResponse;
        try {
            apiResponse = restClient.get()
                    .uri(builder -> builder.path(FORECAST_PATH)
                            .queryParam("serviceKey", apiKey)
                            .queryParam("pageNo", PAGE_NUMBER)
                            .queryParam("numOfRows", ROWS_PER_PAGE)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", DATE.format(baseDate))
                            .queryParam("base_time", TIME.format(baseTime))
                            .queryParam("nx", gridX)
                            .queryParam("ny", gridY)
                            .build())
                    .retrieve()
                    .body(ForecastApiResponse.class);
        } catch (ResourceAccessException exception) {
            log.warn("기상청 단기예보 연결 실패: {}", exception.getClass().getSimpleName());
            throw new WeatherForecastException(WEATHER_FORECAST_SERVICE_UNAVAILABLE,
                    "기상청 연결 실패", exception);
        } catch (RestClientResponseException exception) {
            log.warn("기상청 단기예보 HTTP 오류: status={}", exception.getStatusCode().value());
            throw new WeatherForecastException(WEATHER_FORECAST_LOOKUP_FAILED,
                    "기상청 HTTP 상태: " + exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            log.warn("기상청 단기예보 응답 변환 실패: {}", exception.getClass().getSimpleName());
            throw new WeatherForecastException(WEATHER_FORECAST_RESPONSE_INVALID,
                    "기상청 응답 변환 실패", exception);
        }

        return extractItems(apiResponse);
    }

    private List<KmaForecastItem> extractItems(ForecastApiResponse apiResponse) {
        if (apiResponse == null || apiResponse.response() == null
                || apiResponse.response().header() == null) {
            throw invalid("응답 헤더가 없습니다.");
        }

        var response = apiResponse.response();
        var resultCode = response.header().resultCode();
        if (!"00".equals(resultCode)) {
            throw new WeatherForecastException(WEATHER_FORECAST_LOOKUP_FAILED,
                    "기상청 응답 코드: " + safeResultCode(resultCode));
        }
        if (response.body() == null || response.body().items() == null
                || response.body().items().item() == null
                || response.body().items().item().isEmpty()
                || response.body().items().item().stream().anyMatch(item -> item == null)) {
            throw invalid("예보 항목이 없습니다.");
        }
        return List.copyOf(response.body().items().item());
    }

    private String safeResultCode(String resultCode) {
        return resultCode == null || resultCode.isBlank() ? "UNKNOWN" : resultCode;
    }

    private WeatherForecastException invalid(String reason) {
        return new WeatherForecastException(WEATHER_FORECAST_RESPONSE_INVALID, reason);
    }

    private record ForecastApiResponse(ForecastResponse response) {
    }

    private record ForecastResponse(ForecastHeader header, ForecastBody body) {
    }

    private record ForecastHeader(String resultCode, String resultMsg) {
    }

    private record ForecastBody(String dataType, ForecastItems items, Integer pageNo,
                                Integer numOfRows, Integer totalCount) {
    }

    private record ForecastItems(List<KmaForecastItem> item) {
    }
}
