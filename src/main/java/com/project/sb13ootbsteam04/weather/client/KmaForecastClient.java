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
import java.util.ArrayList;
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
    private static final int FIRST_PAGE_NUMBER = 1;
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

        var requestedBaseDate = DATE.format(baseDate);
        var requestedBaseTime = TIME.format(baseTime);
        var firstPage = extractPage(requestPage(requestedBaseDate, requestedBaseTime,
                gridX, gridY, FIRST_PAGE_NUMBER), requestedBaseDate, requestedBaseTime);
        var items = new ArrayList<>(firstPage.items());

        for (int pageNumber = FIRST_PAGE_NUMBER + 1;
             items.size() < firstPage.totalCount(); pageNumber++) {
            var page = extractPage(requestPage(requestedBaseDate, requestedBaseTime,
                    gridX, gridY, pageNumber), requestedBaseDate, requestedBaseTime);
            items.addAll(page.items());
        }
        return List.copyOf(items);
    }

    private ForecastApiResponse requestPage(String baseDate, String baseTime,
                                            int gridX, int gridY, int pageNumber) {
        try {
            return restClient.get()
                    .uri(builder -> builder.path(FORECAST_PATH)
                            .queryParam("serviceKey", "{serviceKey}")
                            .queryParam("pageNo", pageNumber)
                            .queryParam("numOfRows", ROWS_PER_PAGE)
                            .queryParam("dataType", "JSON")
                            .queryParam("base_date", baseDate)
                            .queryParam("base_time", baseTime)
                            .queryParam("nx", gridX)
                            .queryParam("ny", gridY)
                            .build(apiKey))
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
    }

    private ForecastPage extractPage(ForecastApiResponse apiResponse,
                                     String requestedBaseDate, String requestedBaseTime) {
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
        if (response.body() == null || response.body().totalCount() == null
                || response.body().totalCount() <= 0 || response.body().items() == null
                || response.body().items().item() == null
                || response.body().items().item().isEmpty()) {
            throw invalid("예보 항목이 없습니다.");
        }
        var items = response.body().items().item();
        if (items.stream().anyMatch(item -> item == null)) {
            throw invalid("예보 항목이 올바르지 않습니다.");
        }
        if (items.stream().anyMatch(item -> !requestedBaseDate.equals(item.baseDate())
                || !requestedBaseTime.equals(item.baseTime()))) {
            throw invalid("요청 발표 일시와 응답 발표 일시가 일치하지 않습니다.");
        }
        return new ForecastPage(List.copyOf(items), response.body().totalCount());
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

    private record ForecastPage(List<KmaForecastItem> items, int totalCount) {
    }
}
