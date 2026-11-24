package com.project.sb13ootbsteam04.weather.client;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KmaForecastClientTest {

    private MockRestServiceServer server;
    private KmaForecastClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://apis.data.go.kr");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KmaForecastClient(builder.build(), "test-key");
    }

    @Test
    void requestsVillageForecastAndReturnsItems() {
        server.expect(requestToUriTemplate(
                        "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                                + "?serviceKey={serviceKey}&pageNo={pageNo}&numOfRows={numOfRows}"
                                + "&dataType={dataType}&base_date={baseDate}&base_time={baseTime}&nx={nx}&ny={ny}",
                        "test-key", 1, 1000, "JSON", "20261124", "0230", 60, 127))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("base_date", "20261124"))
                .andExpect(queryParam("base_time", "0230"))
                .andExpect(queryParam("nx", "60"))
                .andExpect(queryParam("ny", "127"))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        var items = client.getVillageForecast(
                LocalDate.of(2026, 11, 24), LocalTime.of(2, 30), 60, 127);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.baseDate()).isEqualTo("20261124");
            assertThat(item.baseTime()).isEqualTo("0230");
            assertThat(item.fcstDate()).isEqualTo("20261124");
            assertThat(item.fcstTime()).isEqualTo("0300");
            assertThat(item.category()).isEqualTo("TMP");
            assertThat(item.fcstValue()).isEqualTo("7");
            assertThat(item.nx()).isEqualTo(60);
            assertThat(item.ny()).isEqualTo(127);
        });
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyWithoutHttpCall() {
        var noKeyClient = new KmaForecastClient(RestClient.create(), " ");

        assertThatThrownBy(() -> noKeyClient.getVillageForecast(
                LocalDate.of(2026, 11, 24), LocalTime.of(2, 30), 60, 127))
                .isInstanceOfSatisfying(WeatherForecastException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.WEATHER_FORECAST_API_KEY_NOT_CONFIGURED);
                    assertThat(exception.getErrorCode().getStatus())
                            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    @Test
    void rejectsKmaErrorResult() {
        server.expect(requestToUriTemplate(
                        "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                                + "?serviceKey={serviceKey}&pageNo={pageNo}&numOfRows={numOfRows}"
                                + "&dataType={dataType}&base_date={baseDate}&base_time={baseTime}&nx={nx}&ny={ny}",
                        "test-key", 1, 1000, "JSON", "20261124", "0230", 60, 127))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"},"body":null}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getVillageForecast(
                LocalDate.of(2026, 11, 24), LocalTime.of(2, 30), 60, 127))
                .isInstanceOfSatisfying(WeatherForecastException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.WEATHER_FORECAST_LOOKUP_FAILED));
        server.verify();
    }

    @Test
    void rejectsMissingItemsAsInvalidResponse() {
        server.expect(requestToUriTemplate(
                        "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                                + "?serviceKey={serviceKey}&pageNo={pageNo}&numOfRows={numOfRows}"
                                + "&dataType={dataType}&base_date={baseDate}&base_time={baseTime}&nx={nx}&ny={ny}",
                        "test-key", 1, 1000, "JSON", "20261124", "0230", 60, 127))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                        "body":{"items":null,"totalCount":1}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getVillageForecast(
                LocalDate.of(2026, 11, 24), LocalTime.of(2, 30), 60, 127))
                .isInstanceOfSatisfying(WeatherForecastException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.WEATHER_FORECAST_RESPONSE_INVALID));
        server.verify();
    }

    @Test
    void mapsHttpErrorToLookupFailure() {
        server.expect(requestToUriTemplate(
                        "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                                + "?serviceKey={serviceKey}&pageNo={pageNo}&numOfRows={numOfRows}"
                                + "&dataType={dataType}&base_date={baseDate}&base_time={baseTime}&nx={nx}&ny={ny}",
                        "test-key", 1, 1000, "JSON", "20261124", "0230", 60, 127))
                .andRespond(withServerError().body("upstream-secret"));

        assertThatThrownBy(() -> client.getVillageForecast(
                LocalDate.of(2026, 11, 24), LocalTime.of(2, 30), 60, 127))
                .isInstanceOfSatisfying(WeatherForecastException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.WEATHER_FORECAST_LOOKUP_FAILED);
                    assertThat(exception.getMessage()).doesNotContain("upstream-secret");
                });
        server.verify();
    }

    @Test
    void mapsTimeoutToServiceUnavailable() {
        server.expect(requestToUriTemplate(
                        "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                                + "?serviceKey={serviceKey}&pageNo={pageNo}&numOfRows={numOfRows}"
                                + "&dataType={dataType}&base_date={baseDate}&base_time={baseTime}&nx={nx}&ny={ny}",
                        "test-key", 1, 1000, "JSON", "20261124", "0230", 60, 127))
                .andRespond(withException(new SocketTimeoutException("test-key")));

        assertThatThrownBy(() -> client.getVillageForecast(
                LocalDate.of(2026, 11, 24), LocalTime.of(2, 30), 60, 127))
                .isInstanceOfSatisfying(WeatherForecastException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.WEATHER_FORECAST_SERVICE_UNAVAILABLE);
                    assertThat(exception.getErrorCode().getStatus())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        server.verify();
    }

    private String successResponse() {
        return """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                    "body": {
                      "dataType": "JSON",
                      "items": {
                        "item": [
                          {"baseDate":"20261124","baseTime":"0230","category":"TMP",
                           "fcstDate":"20261124","fcstTime":"0300","fcstValue":"7",
                           "nx":60,"ny":127}
                        ]
                      },
                      "pageNo": 1,
                      "numOfRows": 1000,
                      "totalCount": 1
                    }
                  }
                }
                """;
    }
}
