FROM openjdk:21-jdk-slim

WORKDIR /app

# Gradle Wrapper와 소스 코드 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

# 실행 권한 부여
RUN chmod +x gradlew

# 애플리케이션 빌드
RUN ./gradlew build -x test

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
CMD ["./gradlew", "bootRun"]