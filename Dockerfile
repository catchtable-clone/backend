# ── 1단계: Gradle 빌드 ─────────────────────────────────────
FROM eclipse-temurin:21.0.6_7-jdk-noble AS builder
WORKDIR /app

# 의존성 캐시 분리 — 코드만 바뀌면 의존성 캐시 재사용
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스 복사 후 bootJar 빌드 (테스트는 CI에서 별도 실행)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ── 2단계: 실행 ────────────────────────────────────────────
FROM eclipse-temurin:21.0.6_7-jre-noble

ENV TZ=Asia/Seoul

WORKDIR /app

# HEALTHCHECK용 wget 설치 (eclipse-temurin jre-noble 이미지에 미포함)
RUN apt-get update \
 && apt-get install -y --no-install-recommends wget \
 && rm -rf /var/lib/apt/lists/*

# 비루트 유저로 실행
RUN useradd -r -u 1001 spring
USER spring

COPY --from=builder /app/build/libs/*.jar app.jar

# t3.micro(1GB RAM) 환경 고려한 JVM 메모리 튜닝
# 컨테이너에 할당된 메모리의 70%를 힙으로 사용 (나머지 30%는 metaspace, 스택, 네이티브)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:InitialRAMPercentage=50.0"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
