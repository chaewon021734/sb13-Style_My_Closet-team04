
package com.project.sb13ootbsteam04.global.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtUserIdResolver {

    public UUID resolve(Authentication authentication) {

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        try {
            return UUID.fromString(jwtAuth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException(
                    "유효하지 않은 사용자 인증 정보입니다."
            );
        }
    }
}
