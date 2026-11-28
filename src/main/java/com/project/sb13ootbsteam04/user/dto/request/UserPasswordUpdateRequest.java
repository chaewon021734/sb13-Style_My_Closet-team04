package com.project.sb13ootbsteam04.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordUpdateRequest(

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(
                min = 6,
                message = "새 비밀번호는 6자 이상이어야 합니다."
        )
        String password

) {
}