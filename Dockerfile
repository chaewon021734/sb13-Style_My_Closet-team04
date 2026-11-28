# syntax=docker/dockerfile:1

# ---------- 1단계: 빌드 스테이지 ----------
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Gradle 설정과 wrapper를 먼저 복사해 소스 변경 시에도 Docker 레이어 캐시를 활용합니다.
COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src

# bootJar 작업은 테스트를 실행하지 않으며, CI에서 테스트를 별도로 수행합니다.
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

# ---------- 2단계: 실행 스테이지 ----------
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# 애플리케이션은 최소 권한의 전용 사용자로 실행합니다.
RUN addgroup --system spring && adduser --system --ingroup spring spring

USER spring:spring

# 실행 이미지에는 빌드 산출물만 포함합니다.
COPY --chown=spring:spring --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

# Fargate task 메모리의 일부를 native memory와 스레드 스택에 남깁니다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
