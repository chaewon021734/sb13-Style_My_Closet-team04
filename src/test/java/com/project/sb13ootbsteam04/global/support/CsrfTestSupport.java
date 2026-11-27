package com.project.sb13ootbsteam04.global.support;

import jakarta.servlet.http.Cookie;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public final class CsrfTestSupport {

    private static final String CSRF_URL =
            "/api/auth/csrf-token";

    private static final String COOKIE_NAME =
            "XSRF-TOKEN";

    private static final String HEADER_NAME =
            "X-XSRF-TOKEN";

    private CsrfTestSupport() {
    }

    // 실제 CSRF 토큰 발급 API를 호출하여 쿠키 획득
    public static Cookie issueCsrfCookie(MockMvc mockMvc)
            throws Exception {

        MvcResult result = mockMvc.perform(get(CSRF_URL))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andReturn();

        Cookie csrfCookie = result.getResponse()
                .getCookie(COOKIE_NAME);

        assertNotNull(csrfCookie);

        return csrfCookie;
    }

    // 요청에 CSRF 쿠키와 헤더를 함께 추가
    public static MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request,
            Cookie csrfCookie
    ) {

        return request
                .cookie(csrfCookie)
                .header(
                        HEADER_NAME,
                        csrfCookie.getValue()
                );
    }
}