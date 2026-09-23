# ---------- 빌드 ----------
# 빌드 도구(Maven·JDK·소스)가 운영 이미지에 남지 않도록 단계를 나눈다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# 의존성만 먼저 받아 레이어로 굳힌다. 소스만 고치면 이 단계는 캐시를 탄다.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B clean package -DskipTests

# ---------- 실행 ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# root 로 돌리지 않는다. 컨테이너가 뚫려도 호스트로 번지기 어렵게 만든다.
RUN addgroup -S flow && adduser -S flow -G flow
USER flow

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
