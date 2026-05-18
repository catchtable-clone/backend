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

# OpenTelemetry Java Agent — 코드 수정 없이 트레이스 자동 수집
# Spring MVC, JDBC, HikariCP, HTTP Client 등 자동 계측
ARG OTEL_AGENT_VERSION=2.10.0
RUN wget -qO /opentelemetry-javaagent.jar \
      https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar \
 && chmod 644 /opentelemetry-javaagent.jar

# 비루트 유저로 실행
RUN useradd -r -u 1001 spring
USER spring

COPY --from=builder /app/build/libs/*.jar app.jar

# t3.small 환경 고려한 JVM 메모리 튜닝 + OTel Agent 자동 활성화
# OTEL_EXPORTER_OTLP_ENDPOINT가 설정 안 되면 트레이스 전송만 실패할 뿐 앱은 정상 동작
ENV JAVA_TOOL_OPTIONS="-javaagent:/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=70.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
