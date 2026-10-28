package com.project.sb13ootbsteam04.clothes.service.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.project.sb13ootbsteam04.clothes.exception.InvalidClothesImageException;
import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

@DisplayName("의상 이미지 Command")
class ClothesImageCommandTest {

  @Test
  @DisplayName("입력 및 반환 배열로 이미지 내용을 변경할 수 없다")
  void 이미지_내용_방어적_복사() {
    byte[] content = pngBytes();
    ClothesImageCommand command = new ClothesImageCommand("image.png", "image/png", content);

    content[0] = 0;
    byte[] returned = command.content();
    returned[0] = 0;

    assertThat(command.content()).containsExactly(pngBytes());
  }

  @Test
  @DisplayName("허용되지 않은 형식과 위조된 이미지 내용은 거부한다")
  void 이미지_형식_검증() {
    assertAll(
        () -> assertThatThrownBy(() -> new ClothesImageCommand(
            "image.svg", "image/svg+xml", "<svg/>".getBytes()))
            .isInstanceOfSatisfying(InvalidClothesImageException.class, exception ->
                assertAll(
                    () -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CLOTHES_IMAGE_TYPE_UNSUPPORTED),
                    () -> assertThat(exception.getDetails()).containsKey("image")
                )),
        () -> assertThatThrownBy(() -> new ClothesImageCommand(
            "fake.png", "image/png", "not-png".getBytes()))
            .isInstanceOfSatisfying(InvalidClothesImageException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.CLOTHES_IMAGE_CONTENT_MISMATCH))
    );
  }

  @ParameterizedTest(name = "{0} 이미지 허용") // 첫 번째로 전달된 인자를 테스트 이름에 포함
  @MethodSource("validImages")
  @DisplayName("허용된 이미지 형식의 파일 시그니처를 검증한다")
  void 허용된_이미지_형식(String contentType, byte[] content) {

    ClothesImageCommand command = new ClothesImageCommand(null, contentType.toUpperCase(), content);

    assertAll(
        () -> assertThat(command.originalFilename()).isEqualTo("image"),
        () -> assertThat(command.contentType()).isEqualTo(contentType),
        () -> assertThat(command.content()).containsExactly(content)
    );
  }

  @Test
  @DisplayName("콘텐츠 타입, 내용 및 크기 제한을 검증한다")
  void 이미지_필수값과_크기_검증() {
    byte[] oversized = new byte[ClothesImageCommand.MAX_SIZE_BYTES + 1];
    System.arraycopy(pngBytes(), 0, oversized, 0, pngBytes().length);

    assertAll(
        () -> assertThatThrownBy(() -> new ClothesImageCommand("image", null, pngBytes()))
            .isInstanceOfSatisfying(InvalidClothesImageException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.CLOTHES_IMAGE_CONTENT_TYPE_REQUIRED)),
        () -> assertThatThrownBy(() -> new ClothesImageCommand(
            "image", "image/png", new byte[0]))
            .isInstanceOfSatisfying(InvalidClothesImageException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_IMAGE_EMPTY)),
        () -> assertThatThrownBy(() -> new ClothesImageCommand(
            "image", "image/png", oversized))
            .isInstanceOfSatisfying(InvalidClothesImageException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_IMAGE_TOO_LARGE))
    );
  }

  private static Stream<Arguments> validImages() {
    return Stream.of(
        Arguments.of("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        Arguments.of("image/gif", "GIF89a".getBytes()),
        Arguments.of("image/webp", new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'})
    );
  }

  private byte[] pngBytes() {
    return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
  }
}
