package com.project.sb13ootbsteam04.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.feed.dto.CreateFeedRequest;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.SkyStatus;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WindStrength;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import java.time.Instant;
import java.net.URLDecoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RecommendationHttpIntegrationTest {

  @LocalServerPort
  private int port;

  @Autowired private UserRepository userRepository;
  @Autowired private WeatherRepository weatherRepository;
  @Autowired private ClothesRepository clothesRepository;
  @Autowired private ClothesAttributeDefRepository clothesAttributeDefRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void servesRecommendationThroughRunningHttpServer() throws Exception {
    User user = userRepository.saveAndFlush(
        new User("추천 사용자", "recommendation-http@example.com", "encoded-password"));
    jdbcTemplate.update(
        "insert into profiles (user_id, temperature_sensitivity) values (?, ?)",
        user.getId(),
        1);

    ClothesAttributeDef season = clothesAttributeDefRepository.saveAndFlush(
        ClothesAttributeDef.create("계절", List.of("여름", "겨울")));
    Clothes winterTop = clothes(user, season, "겨울 상의", ClothesType.TOP, "겨울");
    clothes(user, season, "여름 상의", ClothesType.TOP, "여름");
    clothes(user, season, "겨울 하의", ClothesType.BOTTOM, "겨울");
    clothes(user, season, "겨울 외투", ClothesType.OUTER, "겨울");
    clothes(user, season, "겨울 신발", ClothesType.SHOES, "겨울");

    Weather weather = weatherRepository.saveAndFlush(Weather.builder()
        .locationId(UUID.randomUUID())
        .forecastedAt(Instant.parse("2026-11-28T02:00:00Z"))
        .forecastAt(Instant.parse("2026-11-28T03:00:00Z"))
        .forecastStepMinutes(60)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmountRaw("강수없음")
        .precipitationAmount(0.0)
        .precipitationProbability(0.0)
        .humidityCurrent(50.0)
        .temperatureCurrent(10.0)
        .windSpeedRaw("2.0")
        .windSpeed(2.0)
        .windSpeedAsWord(WindStrength.WEAK)
        .build());

    RestClient client = RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .build();
    String path = "/api/recommendations?weatherId=" + weather.getId();

    var unauthorized = client.get().uri(path).exchange((request, response) -> response.getStatusCode());
    assertThat(unauthorized).isEqualTo(HttpStatus.UNAUTHORIZED);

    String accessToken = jwtTokenProvider.generateAccessToken(user);
    var success = client.get()
        .uri(path)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .exchange((request, response) -> new HttpResult(
            response.getStatusCode().value(),
            new String(response.getBody().readAllBytes())));

    assertThat(success.status()).isEqualTo(HttpStatus.OK.value());
    JsonNode recommendation = objectMapper.readTree(success.body());
    assertThat(recommendation.path("weatherId").asText()).isEqualTo(weather.getId().toString());
    assertThat(recommendation.path("userId").asText()).isEqualTo(user.getId().toString());
    JsonNode recommendedClothes = recommendation.path("clothes");
    assertThat(IntStream.range(0, recommendedClothes.size())
        .mapToObj(index -> recommendedClothes.get(index).path("type").asText())
        .toList())
        .containsExactly("TOP", "BOTTOM", "OUTER", "SHOES");
    assertThat(recommendedClothes.get(0).path("clothesId").asText())
        .isEqualTo(winterTop.getId().toString());

    List<UUID> recommendedClothesIds = IntStream.range(0, recommendedClothes.size())
        .mapToObj(index -> UUID.fromString(
            recommendedClothes.get(index).path("clothesId").asText()))
        .toList();
    Csrf csrf = issueCsrf();
    var createdFeed = client.post()
        .uri("/api/feeds")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header(HttpHeaders.COOKIE, csrf.cookie())
        .header("X-XSRF-TOKEN", csrf.token())
        .body(new CreateFeedRequest(
            user.getId(),
            weather.getId(),
            recommendedClothesIds,
            "오늘의 추천 OOTD"))
        .exchange((request, response) -> new HttpResult(
            response.getStatusCode().value(),
            new String(response.getBody().readAllBytes())));

    assertThat(createdFeed.status()).isEqualTo(HttpStatus.CREATED.value());
    JsonNode feed = objectMapper.readTree(createdFeed.body());
    assertThat(feed.path("author").path("userId").asText())
        .isEqualTo(user.getId().toString());
    assertThat(feed.path("weather").path("weatherId").asText())
        .isEqualTo(weather.getId().toString());
    JsonNode ootds = feed.path("ootds");
    assertThat(IntStream.range(0, ootds.size())
        .mapToObj(index -> UUID.fromString(ootds.get(index).path("clothesId").asText()))
        .toList())
        .containsExactlyElementsOf(recommendedClothesIds);

    UUID missingWeatherId = UUID.randomUUID();
    var notFound = client.get()
        .uri("/api/recommendations?weatherId=" + missingWeatherId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .exchange((request, response) -> new HttpResult(
            response.getStatusCode().value(),
            new String(response.getBody().readAllBytes())));

    assertThat(notFound.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
    JsonNode error = objectMapper.readTree(notFound.body());
    assertThat(error.path("exceptionName").asText()).isEqualTo("RecommendationException");
    assertThat(error.path("details").path("weatherId").asText())
        .isEqualTo(missingWeatherId.toString());
  }

  private Clothes clothes(
      User owner,
      ClothesAttributeDef season,
      String name,
      ClothesType type,
      String seasonValue
  ) {
    Clothes clothes = Clothes.create(owner, name, type);
    ClothesAttribute.create(clothes, season, seasonValue);
    return clothesRepository.saveAndFlush(clothes);
  }

  private Csrf issueCsrf() throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/api/auth/csrf-token"))
        .GET()
        .build();
    HttpResponse<Void> response = HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
    String setCookie = response.headers()
        .allValues("set-cookie")
        .stream()
        .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
        .findFirst()
        .orElseThrow(() -> new AssertionError(response.headers().map().toString()));
    String cookie = setCookie.substring(0, setCookie.indexOf(';'));
    String token = URLDecoder.decode(
        cookie.substring("XSRF-TOKEN=".length()),
        StandardCharsets.UTF_8);
    return new Csrf(cookie, token);
  }

  private record HttpResult(int status, String body) {
  }

  private record Csrf(String cookie, String token) {
  }
}
