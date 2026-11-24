package com.project.sb13ootbsteam04.auth.filter;

import com.project.sb13ootbsteam04.auth.controller.TemporaryPasswordController;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import org.springframework.security.oauth2.jwt.JwtDecoder;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TemporaryPasswordController.class)
@Import({
        SecurityConfig.class,
        TemporaryPasswordRateLimitFilter.class
})
class TemporaryPasswordRateLimitWebTest {

    private static final String URL =
            "/api/auth/temporary-password";

    private static final String TEST_IP =
            "198.51.100.27";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemporaryPasswordService temporaryPasswordService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ========================================
    // 실제 HTTP 필터 적용 검증
    // 1~5회 허용, 6회 차단
    // ========================================

    @Test
    void shouldAllowFiveRequestsAndRejectSixth()
            throws Exception {

        // 1. 동일 IP로 5회 요청
        for (int i = 0; i < 5; i++) {

            mockMvc.perform(
                            post(URL)
                                    // 필터가 검사할 servletPath 명시
                                    .servletPath(URL)
                                    .with(request -> {
                                        request.setRemoteAddr(TEST_IP);
                                        return request;
                                    })
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                    {
                                      "email": "test@example.com"
                                    }
                                    """)
                    )
                    .andExpect(status().isAccepted());
        }

        // Service가 정확히 5번 호출되었는지 확인
        verify(temporaryPasswordService, times(5))
                .issueTemporaryPassword("test@example.com");

        // 2. 같은 IP에서 6번째 요청
        mockMvc.perform(
                        post(URL)
                                // 필터가 검사할 servletPath 명시
                                .servletPath(URL)
                                .with(request -> {
                                    request.setRemoteAddr(TEST_IP);
                                    return request;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "email": "test@example.com"
                                }
                                """)
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // 6번째 요청은 Controller까지 도달하지 않아야 함
        verify(temporaryPasswordService, times(5))
                .issueTemporaryPassword("test@example.com");
    }
}
