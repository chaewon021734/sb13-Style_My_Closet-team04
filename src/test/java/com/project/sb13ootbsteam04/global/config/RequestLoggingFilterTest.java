package com.project.sb13ootbsteam04.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

  private final RequestLoggingFilter filter = new RequestLoggingFilter();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void 요청_추적_ID를_응답하고_MDC를_정리한다() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clothes");
    request.setQueryString("ownerId=sensitive-value");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean();
    MDC.put("traceId", "upstream-trace");

    // when
    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      chainInvoked.set(true);
      assertThat(MDC.get(RequestLoggingFilter.REQUEST_METHOD)).isEqualTo("GET");
      assertThat(MDC.get(RequestLoggingFilter.REQUEST_URI)).isEqualTo("/api/clothes");
      assertThat(MDC.get(RequestLoggingFilter.REQUEST_URI)).doesNotContain("ownerId");
    });

    // then
    assertThat(chainInvoked).isTrue();
    assertThatCodeIsUuid(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER));
    assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("traceId", "upstream-trace"));
  }

  @Test
  void 후속_필터가_실패해도_MDC를_정리한다() {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/clothes");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when & then
    assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      throw new ServletException("downstream failure");
    }))
        .isInstanceOf(ServletException.class)
        .hasMessage("downstream failure");
    assertThat(MDC.getCopyOfContextMap()).isNull();
  }

  private static void assertThatCodeIsUuid(String value) {
    assertThat(value).isNotBlank();
    assertThat(UUID.fromString(value)).isNotNull();
  }
}
