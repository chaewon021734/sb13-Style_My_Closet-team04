package com.project.sb13ootbsteam04.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  INVALID_REQUEST("GLB_001", HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
  INTERNAL_SERVER_ERROR("GLB_999", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),

  // CLOTHES
  CLOTHES_IMAGE_CONTENT_TYPE_REQUIRED(
      "CLT_001", HttpStatus.BAD_REQUEST, "이미지 콘텐츠 타입은 필수입니다"),
  CLOTHES_IMAGE_TYPE_UNSUPPORTED(
      "CLT_002", HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다"),
  CLOTHES_IMAGE_EMPTY(
      "CLT_003", HttpStatus.BAD_REQUEST, "빈 이미지 파일은 등록할 수 없습니다"),
  CLOTHES_IMAGE_TOO_LARGE(
      "CLT_004", HttpStatus.BAD_REQUEST, "이미지는 10MB를 초과할 수 없습니다"),
  CLOTHES_IMAGE_CONTENT_MISMATCH(
      "CLT_005", HttpStatus.BAD_REQUEST, "이미지 내용과 콘텐츠 타입이 일치하지 않습니다"),
  CLOTHES_IMAGE_READ_FAILED(
      "CLT_006", HttpStatus.INTERNAL_SERVER_ERROR, "이미지 파일을 읽을 수 없습니다"),
  CLOTHES_ATTRIBUTE_DUPLICATED(
      "CLT_007", HttpStatus.BAD_REQUEST, "같은 속성 정의를 중복해서 등록할 수 없습니다"),
  CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND(
      "CLT_008", HttpStatus.BAD_REQUEST, "존재하지 않는 의상 속성 정의가 있습니다"),
  CLOTHES_IMAGE_STORAGE_NOT_CONFIGURED(
      "CLT_009", HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장소가 설정되지 않았습니다"),
  CLOTHES_IMAGE_UPLOAD_FAILED(
      "CLT_010", HttpStatus.INTERNAL_SERVER_ERROR, "의상 이미지를 저장하지 못했습니다"),
  CLOTHES_IMAGE_DELETE_FAILED(
      "CLT_011", HttpStatus.INTERNAL_SERVER_ERROR, "의상 이미지를 삭제하지 못했습니다"),
  CLOTHES_OWNER_NOT_FOUND(
      "CLT_012", HttpStatus.BAD_REQUEST, "존재하지 않는 의상 소유자입니다"),
  CLOTHES_ATTRIBUTE_VALUE_INVALID(
      "CLT_013", HttpStatus.BAD_REQUEST, "선택할 수 없는 의상 속성 값입니다");

  private final String code;
  private final HttpStatus status;
  private final String message;

  ErrorCode(String code, HttpStatus status, String message) {
    this.code = code;
    this.status = status;
    this.message = message;
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }
}
