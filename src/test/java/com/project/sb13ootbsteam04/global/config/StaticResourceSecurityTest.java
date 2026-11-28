package com.project.sb13ootbsteam04.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = StaticResourceSecurityTest.TestController.class
)
@Import(SecurityConfig.class)
class StaticResourceSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("로그인 없이 웹사이트 HTML에 접근할 수 있다")
    void shouldAllowIndexHtml() throws Exception {

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 없이 JavaScript 파일에 접근할 수 있다")
    void shouldAllowJavaScript() throws Exception {

        mockMvc.perform(
                get("/assets/index-CqeESGX6.js")
        ).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 없이 CSS 파일에 접근할 수 있다")
    void shouldAllowCss() throws Exception {

        mockMvc.perform(
                get("/assets/index-B6awMYOr.css")
        ).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 없이 로고 이미지에 접근할 수 있다")
    void shouldAllowLogo() throws Exception {

        mockMvc.perform(
                get("/logo_symbol.svg")
        ).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 없이 vite 이미지에 접근할 수 있다")
    void shouldAllowViteImage() throws Exception {

        mockMvc.perform(
                get("/vite.svg")
        ).andExpect(status().isOk());
    }

    @Test
    @DisplayName("정적 리소스 접근 허용 이후에도 사용자 API는 인증이 필요하다")
    void shouldProtectUserApi() throws Exception {

        mockMvc.perform(
                get("/api/users")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정적 리소스 접근 허용 이후에도 관리자 API는 인증이 필요하다")
    void shouldProtectAdminApi() throws Exception {

        mockMvc.perform(
                get("/api/admin/users")
        ).andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestController {
    }
}