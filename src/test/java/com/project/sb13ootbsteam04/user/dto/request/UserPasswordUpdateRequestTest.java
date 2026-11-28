package com.project.sb13ootbsteam04.user.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("비밀번호 변경 요청 DTO 검증")
class UserPasswordUpdateRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("비밀번호가 6자이면 검증에 성공한다")
    void 비밀번호_6자_성공() {
        // given
        UserPasswordUpdateRequest request =
                new UserPasswordUpdateRequest("abc123");

        // when
        Set<ConstraintViolation<UserPasswordUpdateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }
}
