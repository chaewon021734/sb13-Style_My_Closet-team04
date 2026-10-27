package com.project.sb13ootbsteam04.weather.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.sb13ootbsteam04.weather.exception.WeatherLocationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
@Slf4j
public class KakaoLocationClient {

    private final RestClient restClient;
    private final String apiKey;

    public KakaoLocationClient(@Qualifier("kakaoRestClient") RestClient restClient,
                               @Value("${weather.kakao.api-key:}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public List<String> getLocationNames(double latitude, double longitude) {
        if (apiKey.isBlank()) {
            throw new WeatherLocationException("카카오 위치 조회 API 키가 설정되지 않았습니다.");
        }
        RegionResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/v2/local/geo/coord2regioncode.json")
                            .queryParam("x", longitude).queryParam("y", latitude).build())
                    .header("Authorization", "KakaoAK " + apiKey)
                    .retrieve().body(RegionResponse.class);
        } catch (RestClientException exception) {
            // 외부 응답 본문이나 인증 키를 클라이언트에 노출하지 않는다.
            log.warn("카카오 행정구역 조회 실패: {}", exception.getClass().getSimpleName());
            throw new WeatherLocationException("행정구역 정보를 조회할 수 없습니다.");
        }
        if (response == null || response.documents() == null) {
            throw new WeatherLocationException("행정구역 조회 응답이 올바르지 않습니다.");
        }
        Region region = response.documents().stream()
                .filter(Objects::nonNull)
                .filter(document -> "H".equals(document.type())).findFirst()
                .orElseThrow(() -> new WeatherLocationException("해당 좌표의 행정구역을 찾을 수 없습니다."));
        List<String> names = Stream.of(region.first(), region.second(), region.third())
                .filter(name -> name != null && !name.isBlank()).toList();
        if (names.isEmpty()) {
            throw new WeatherLocationException("해당 좌표의 행정구역을 찾을 수 없습니다.");
        }
        return names;
    }

    public record RegionResponse(List<Region> documents) {
    }

    public record Region(
            @JsonProperty("region_type") String type,
            @JsonProperty("region_1depth_name") String first,
            @JsonProperty("region_2depth_name") String second,
            @JsonProperty("region_3depth_name") String third
    ) {
    }
}
