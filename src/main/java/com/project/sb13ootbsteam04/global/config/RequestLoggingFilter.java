package com.project.sb13ootbsteam04.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

  static final String REQUEST_ID = "requestId";
  static final String REQUEST_METHOD = "requestMethod";
  static final String REQUEST_URI = "requestUri";
  static final String REQUEST_ID_HEADER = "X-Request-Id";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    String requestId = UUID.randomUUID().toString();
    Map<String, String> previousContext = MDC.getCopyOfContextMap();

    try {
      // Query string은 민감 정보가 포함될 수 있어 URI path만 로깅 문맥에 넣습니다.
      MDC.put(REQUEST_ID, requestId);
      MDC.put(REQUEST_METHOD, request.getMethod());
      MDC.put(REQUEST_URI, request.getRequestURI());
      response.setHeader(REQUEST_ID_HEADER, requestId);
      filterChain.doFilter(request, response);
    } finally {
      restoreContext(previousContext);
    }
  }

  private void restoreContext(Map<String, String> previousContext) {
    if (previousContext == null) {
      MDC.clear();
      return;
    }
    MDC.setContextMap(previousContext);
  }
}
