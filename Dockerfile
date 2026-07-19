# 빌드 이미지 생성단계
FROM gradle:9.5.1-jdk25 AS build
WORKDIR /home/app
COPY --chown=gradle:gradle . /home/app
RUN chmod +x ./gradlew && ./gradlew build -x test
# -------------------------
# Build 단계에서 생성된 JAR을 이용
FROM eclipse-temurin:25-jre
VOLUME /tmp
EXPOSE 8080
COPY --from=build /home/app/build/libs/*.jar /app.jar
ENTRYPOINT ["sh", "-c", "java -Dspring.profiles.default=prod -jar /app.jar"]