package com.project.sb13ootbsteam04.user;

import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.StoredImage;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

// 실제 보안 설정과 JWT로 프로필 조회·수정을 검증한다. 외부 S3 저장소만 대체한다.
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("프로필 조회·수정")
class ProfileIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private ClothesImageStorage imageStorage;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final Map<String, Object> LOCATION = Map.of(
            "latitude", 37.5665,
            "longitude", 126.978,
            "x", 60,
            "y", 127,
            "locationNames", List.of("서울특별시", "중구", "태평로1가"));

    @Test
    @DisplayName("프로필이 없는 사용자가 자기 프로필을 수정하면 모든 값이 저장되고 다시 조회된다")
    void createsProfileOnFirstUpdate() throws Exception {
        var user = saveUser("처음");

        var response = update(user, user, Map.of(
                "name", "바뀐이름",
                "gender", "FEMALE",
                "birthDate", "1999-03-15",
                "location", LOCATION,
                "temperatureSensitivity", 4), null);

        assertThat(response.andReturn().getResponse().getStatus()).isEqualTo(200);
        for (var profile : List.of(body(response), body(fetch(user, user)))) {
            assertThat(profile.get("userId").asString()).isEqualTo(user.getId().toString());
            assertThat(profile.get("name").asString()).isEqualTo("바뀐이름");
            assertThat(profile.get("gender").asString()).isEqualTo("FEMALE");
            assertThat(profile.get("birthDate").asString()).isEqualTo("1999-03-15");
            assertThat(profile.get("temperatureSensitivity").asInt()).isEqualTo(4);
            assertThat(profile.get("location").get("x").asInt()).isEqualTo(60);
            assertThat(profile.get("location").get("locationNames").get(0).asString()).isEqualTo("서울특별시");
        }
    }

    @Test
    @DisplayName("일부 값만 보내면 보낸 값만 바뀌고 나머지는 유지된다")
    void keepsFieldsThatWereNotSent() throws Exception {
        var user = saveUser("부분수정");
        update(user, user, Map.of("name", "유지될이름", "gender", "MALE", "location", LOCATION,
                "temperatureSensitivity", 2), null);

        var profile = body(update(user, user, Map.of("temperatureSensitivity", 5), null));

        assertThat(profile.get("temperatureSensitivity").asInt()).isEqualTo(5);
        assertThat(profile.get("name").asString()).isEqualTo("유지될이름");
        assertThat(profile.get("gender").asString()).isEqualTo("MALE");
        assertThat(profile.get("location").get("y").asInt()).isEqualTo(127);
    }

    @Test
    @DisplayName("다른 사용자의 프로필은 수정할 수 없다")
    void rejectsUpdatingOthersProfile() throws Exception {
        var owner = saveUser("주인");
        var other = saveUser("남");

        var response = update(other, owner, Map.of("name", "가로채기"), null);

        assertThat(response.andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(body(fetch(owner, owner)).get("name").asString()).isEqualTo("주인");
    }

    @Test
    @DisplayName("온도 민감도가 1~5를 벗어나면 400이고 값이 바뀌지 않는다")
    void rejectsSensitivityOutOfRange() throws Exception {
        var user = saveUser("민감도");
        update(user, user, Map.of("temperatureSensitivity", 5), null);

        var response = update(user, user, Map.of("temperatureSensitivity", 6), null);

        assertThat(response.andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(body(fetch(user, user)).get("temperatureSensitivity").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("이미지와 함께 수정하면 저장소에 올린 이미지 URL이 프로필 이미지가 된다")
    void savesUploadedImageUrl() throws Exception {
        var user = saveUser("사진");
        given(imageStorage.upload(any())).willReturn(new StoredImage("clothes/a.png", "https://cdn.test/a.png"));

        var profile = body(update(user, user, Map.of("name", "사진"),
                new MockMultipartFile("image", "a.png", "image/png", PNG)));

        assertThat(profile.get("profileImageUrl").asString()).isEqualTo("https://cdn.test/a.png");
    }

    @Test
    @DisplayName("이미지 내용이 형식과 다르면 실패하고 다른 값도 바뀌지 않는다")
    void rejectsInvalidImageWithoutPartialUpdate() throws Exception {
        var user = saveUser("원래이름");

        var response = update(user, user, Map.of("name", "바뀌면안됨"),
                new MockMultipartFile("image", "a.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8)));

        assertThat(response.andReturn().getResponse().getStatus()).isEqualTo(400);
        var profile = body(fetch(user, user));
        assertThat(profile.get("name").asString()).isEqualTo("원래이름");
        assertThat(profile.get("profileImageUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("프로필을 만든 적 없는 사용자도 이름만 채워 조회된다")
    void returnsNameOnlyWhenProfileMissing() throws Exception {
        var viewer = saveUser("보는사람");
        var target = saveUser("프로필없음");

        var response = fetch(viewer, target);

        assertThat(response.andReturn().getResponse().getStatus()).isEqualTo(200);
        var profile = body(response);
        assertThat(profile.get("userId").asString()).isEqualTo(target.getId().toString());
        assertThat(profile.get("name").asString()).isEqualTo("프로필없음");
        for (var field : List.of("gender", "birthDate", "location", "temperatureSensitivity", "profileImageUrl")) {
            assertThat(profile.get(field) == null || profile.get(field).isNull()).as(field).isTrue();
        }
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 프로필은 404다")
    void returnsNotFoundForUnknownUser() throws Exception {
        var viewer = saveUser("조회자");

        var response = mockMvc.perform(get("/api/users/{userId}/profiles", UUID.randomUUID())
                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(viewer)));

        assertThat(response.andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    private User saveUser(String name) {
        return userRepository.save(new User(name, UUID.randomUUID() + "@test.io", "encoded"));
    }

    private ResultActions fetch(User viewer, User target) throws Exception {
        return mockMvc.perform(get("/api/users/{userId}/profiles", target.getId())
                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(viewer)));
    }

    private ResultActions update(User loginUser, User target, Map<String, Object> request, MockMultipartFile image)
            throws Exception {
        var builder = multipart("/api/users/{userId}/profiles", target.getId())
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(request)));
        if (image != null) {
            builder.file(image);
        }
        return mockMvc.perform(builder
                .with(req -> {
                    req.setMethod("PATCH");
                    return req;
                })
                .with(csrf())
                .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(loginUser)));
    }

    private JsonNode body(ResultActions response) throws Exception {
        return objectMapper.readTree(response.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
