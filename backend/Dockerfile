FROM gradle:8.5-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle buildFatJar --no-daemon

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/livekit-meeting-backend.jar
ENTRYPOINT ["java","-jar","/app/livekit-meeting-backend.jar"]
