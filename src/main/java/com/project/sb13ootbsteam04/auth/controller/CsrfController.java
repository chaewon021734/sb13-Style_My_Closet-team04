package com.project.sb13ootbsteam04.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    /**
     * 프론트엔드에서 사용하는 CSRF 토큰을 발급한다.
     * 로그인 이전에도 호출할 수 있다.
     */
    @GetMapping("/csrf-token")
    public ResponseEntity<Void> getCsrfToken(CsrfToken csrfToken) {

        // 지연 생성된 CSRF 토큰을 실제로 생성한다.
        csrfToken.getToken();

        return ResponseEntity.noContent().build();
    }
}