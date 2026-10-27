package com.project.sb13ootbsteam04.clothes.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.project.sb13ootbsteam04.clothes.exception.ClothesImageStorageException;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;
import com.project.sb13ootbsteam04.clothes.storage.StoredImage;
import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@DisplayName("S3 의상 이미지 저장소")
@ExtendWith(MockitoExtension.class)
class S3ClothesImageStorageTest {

  @Mock
  private S3Client s3Client;

  @Test
  @DisplayName("콘텐츠 타입에 맞는 키로 이미지를 업로드하고 공개 URL을 반환한다")
  void 이미지_업로드() {

    S3ClothesImageStorage storage = storage();
    ClothesImageCommand image = pngImage();

    given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .willReturn(PutObjectResponse.builder().build());

    StoredImage result = storage.upload(image);

    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    then(s3Client).should().putObject(captor.capture(), any(RequestBody.class));
    PutObjectRequest request = captor.getValue();

    assertAll(
        () -> assertThat(request.bucket()).isEqualTo("otboo-images"),
        () -> assertThat(request.key()).startsWith("clothes/").endsWith(".png"),
        () -> assertThat(request.contentType()).isEqualTo("image/png"),
        () -> assertThat(request.contentLength()).isEqualTo((long) image.content().length),
        () -> assertThat(result.objectKey()).isEqualTo(request.key()),
        () -> assertThat(result.url()).isEqualTo("https://cdn.example.com/" + request.key())
    );
  }

  @Test
  @DisplayName("객체 키로 S3 이미지를 삭제한다")
  void 이미지_삭제() {

    S3ClothesImageStorage storage = storage();

    storage.delete("clothes/image.png");

    ArgumentCaptor<DeleteObjectRequest> captor =
        ArgumentCaptor.forClass(DeleteObjectRequest.class);

    then(s3Client).should().deleteObject(captor.capture());
    DeleteObjectRequest request = captor.getValue();

    assertAll(
        () -> assertThat(request.bucket()).isEqualTo("otboo-images"),
        () -> assertThat(request.key()).isEqualTo("clothes/image.png")
    );
  }

  @Test
  @DisplayName("S3 업로드 오류를 저장소 예외로 변환한다")
  void 업로드_실패() {

    S3ClothesImageStorage storage = storage();
    given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .willThrow(S3Exception.builder().message("S3 실패").build());

    assertThatThrownBy(() -> storage.upload(pngImage()))
        .isInstanceOfSatisfying(ClothesImageStorageException.class, exception -> {
          assertAll(
              () -> assertThat(exception.getErrorCode())
                  .isEqualTo(ErrorCode.CLOTHES_IMAGE_UPLOAD_FAILED),
              () -> assertThat(exception.getDetails()).containsEntry("operation", "upload"),
              () -> assertThat(exception.getCause()).isInstanceOf(S3Exception.class)
          );
        });
  }

  @Test
  @DisplayName("CDN 주소가 없으면 리전 기반 S3 URL을 반환한다")
  void 기본_S3_URL() {

    S3ClothesImageStorage storage = new S3ClothesImageStorage(
        s3Client,
        new S3ClothesImageProperties("otboo-images", "ap-northeast-2", null)
    );
    ClothesImageCommand image = new ClothesImageCommand(
        "image.jpg",
        "image/jpeg",
        new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
    );

    StoredImage result = storage.upload(image);

    assertAll(
        () -> assertThat(result.objectKey()).endsWith(".jpg"),
        () -> assertThat(result.url()).isEqualTo("https://otboo-images.s3.ap-northeast-2.amazonaws.com/"
                + result.objectKey())
    );
  }

  @Test
  @DisplayName("S3 삭제 실패를 저장소 예외로 변환한다")
  void 삭제_실패() {

    S3ClothesImageStorage storage = storage();
    doThrow(S3Exception.builder().message("S3 실패").build())
        .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

    assertThatThrownBy(() -> storage.delete("clothes/image.png"))
        .isInstanceOfSatisfying(ClothesImageStorageException.class, exception -> {
          assertAll(
              () -> assertThat(exception.getErrorCode())
                  .isEqualTo(ErrorCode.CLOTHES_IMAGE_DELETE_FAILED),
              () -> assertThat(exception.getDetails()).containsEntry("operation", "delete"),
              () -> assertThat(exception.getCause()).isInstanceOf(S3Exception.class)
          );
        });
  }

  @Test
  @DisplayName("저장소 종료 시 S3 클라이언트를 닫는다")
  void 저장소_종료() {
    storage().close();

    then(s3Client).should().close();
  }

  private S3ClothesImageStorage storage() {
    return new S3ClothesImageStorage(
        s3Client,
        new S3ClothesImageProperties(
            "otboo-images",
            "ap-northeast-2",
            "https://cdn.example.com/"
        )
    );
  }

  private ClothesImageCommand pngImage() {
    return new ClothesImageCommand(
        "image.png",
        "image/png",
        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
    );
  }
}
