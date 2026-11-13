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

@DisplayName("회원가입 요청 DTO 검증")
class UserCreateRequestTest {

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
    @DisplayName("이름이 20자이면 검증에 성공한다")
    void 이름_20자_성공() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "가".repeat(20),
                "test@example.com",
                "password123"
        );

        // when
        Set<ConstraintViolation<UserCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이름이 21자이면 검증에 실패한다")
    void 이름_21자_실패() {
        // given
        UserCreateRequest request = new UserCreateRequest(
                "가".repeat(21),
                "test@example.com",
                "password123"
        );

        // when
        Set<ConstraintViolation<UserCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("name");
                    assertThat(violation.getMessage())
                            .isEqualTo("이름은 20자 이하여야 합니다.");
                });
    }


    @Test
    @DisplayName("이메일이 255자이면 검증에 성공한다")
    void 이메일_255자_성공() {
        // given
        String email = "a".repeat(60)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(62)
                + ".com";

        UserCreateRequest request = new UserCreateRequest(
                "홍길동",
                email,
                "password123"
        );

        // when
        Set<ConstraintViolation<UserCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(email).hasSize(255);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이메일이 256자이면 길이 검증에 실패한다")
    void 이메일_256자_실패() {
        // given
        String email = "a".repeat(60)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(63)
                + ".com";

        UserCreateRequest request = new UserCreateRequest(
                "홍길동",
                email,
                "password123"
        );

        // when
        Set<ConstraintViolation<UserCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(email).hasSize(256);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("email");

                    assertThat(violation.getMessage())
                            .isEqualTo("이메일은 255자 이하여야 합니다.");
                });
    }
}
