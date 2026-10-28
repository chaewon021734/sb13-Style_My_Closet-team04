package com.project.sb13ootbsteam04.clothes.service.dto;

import com.project.sb13ootbsteam04.clothes.exception.InvalidClothesImageException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record ClothesImageCommand(
    String originalFilename,
    String contentType,
    byte[] content
) {

  public static final int MAX_SIZE_BYTES = 10 * 1024 * 1024;
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
      "image/jpeg",
      "image/png",
      "image/gif",
      "image/webp"
  );

  public ClothesImageCommand {
    originalFilename = originalFilename == null ? "image" : originalFilename;

    if (contentType == null || contentType.isBlank()) {
      throw InvalidClothesImageException.contentTypeRequired();
    }

    contentType = contentType.toLowerCase(Locale.ROOT);
    content = Objects.requireNonNull(content, "이미지 내용은 필수입니다").clone();

    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw InvalidClothesImageException.unsupportedType();
    }
    if (content.length == 0) {
      throw InvalidClothesImageException.empty();
    }
    if (content.length > MAX_SIZE_BYTES) {
      throw InvalidClothesImageException.tooLarge();
    }
    if (!hasExpectedSignature(contentType, content)) {
      throw InvalidClothesImageException.contentMismatch();
    }
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  private static boolean hasExpectedSignature(String contentType, byte[] bytes) {
    return switch (contentType) {
      case "image/jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
      case "image/png" -> startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
      case "image/gif" -> startsWith(bytes, 'G', 'I', 'F', '8')
          && bytes.length >= 6
          && (bytes[4] == '7' || bytes[4] == '9')
          && bytes[5] == 'a';
      case "image/webp" -> startsWith(bytes, 'R', 'I', 'F', 'F')
          && bytes.length >= 12
          && bytes[8] == 'W'
          && bytes[9] == 'E'
          && bytes[10] == 'B'
          && bytes[11] == 'P';
      default -> false;
    };
  }

  private static boolean startsWith(byte[] bytes, int... signature) {
    if (bytes.length < signature.length) {
      return false;
    }
    for (int index = 0; index < signature.length; index++) {
      if ((bytes[index] & 0xFF) != signature[index]) {
        return false;
      }
    }
    return true;
  }
}
