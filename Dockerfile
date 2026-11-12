# ---------- 1단계: 빌드 스테이지 ----------
# Gradle 빌드에만 필요한 JDK 풀버전 이미지를 사용한다.
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

# 컨테이너 안에서도 root로 프로세스를 띄우지 않기 위해 전용 사용자를 만든다.
# 컨테이너가 뚫려도 root 권한 탈취로 바로 이어지지 않도록 하는 최소한의 방어다.
RUN addgroup --system spring && adduser --system --ingroup spring spring

USER spring:spring

# 빌드 스테이지에서 만들어진 jar만 복사한다. (소스코드, gradle 캐시 등은 최종 이미지에 포함되지 않음)
# --chown을 명시해서 jar도 spring 소유로 복사되도록 한다.
COPY --chown=spring:spring --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

# -XX:MaxRAMPercentage: 고정된 -Xmx 대신 컨테이너에 할당된 메모리의 비율로 힙을 잡는다.
# EC2 인스턴스 사양을 나중에 바꿔도(t3.micro→t3.small 등) Dockerfile을 수정할 필요가 없다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
