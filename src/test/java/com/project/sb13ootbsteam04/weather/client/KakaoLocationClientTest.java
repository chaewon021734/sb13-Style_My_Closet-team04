package com.project.sb13ootbsteam04.weather.client;

import com.project.sb13ootbsteam04.weather.exception.WeatherLocationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KakaoLocationClientTest {

    private MockRestServiceServer server;
    private KakaoLocationClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoLocationClient(builder.build(), "test-key");
    }

    @Test
    void sendsLongitudeAsXAndSelectsAdministrativeRegion() {
        server.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=127.0&y=37.0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess("""
                        {"documents": [
                          {"region_type":"B","region_1depth_name":"법정동"},
                          {"region_type":"H","region_1depth_name":"서울특별시",
                           "region_2depth_name":"중구","region_3depth_name":"명동"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getLocationNames(37, 127)).containsExactly("서울특별시", "중구", "명동");
        server.verify();
    }

    @Test
    void rejectsEmptyResults() {
        server.expect(anything()).andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getLocationNames(37, 127))
                .isInstanceOf(WeatherLocationException.class).hasMessageContaining("찾을 수 없습니다");
        server.verify();
    }

    @Test
    void hidesUpstreamErrorBody() {
        server.expect(anything()).andRespond(withServerError().body("upstream-secret"));
        assertThatThrownBy(() -> client.getLocationNames(37, 127))
                .isInstanceOf(WeatherLocationException.class)
                .hasMessage("행정구역 정보를 조회할 수 없습니다.");
        server.verify();
    }

    @Test
    void rejectsMissingKeyWithoutHttpCall() {
        var noKeyClient = new KakaoLocationClient(RestClient.create(), "");
        assertThatThrownBy(() -> noKeyClient.getLocationNames(37, 127))
                .isInstanceOf(WeatherLocationException.class).hasMessageContaining("키가 설정되지 않았습니다");
    }

    @Test
    void rejectsMalformedResponse() {
        server.expect(anything()).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getLocationNames(37, 127))
                .isInstanceOf(WeatherLocationException.class);
        server.verify();
    }
}
