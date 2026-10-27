package com.project.sb13ootbsteam04.clothes.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

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
