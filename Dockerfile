FROM maven:latest as build
WORKDIR /home/app

COPY src src
COPY pom.xml .

RUN mvn -f /home/app/pom.xml clean package

########
FROM openjdk:20-ea-1-slim
WORKDIR /home/app

ARG NAME="CommsBot-1.0"
COPY --from=build /home/app/target/${NAME}-shaded.jar bot.jar

ENTRYPOINT ["java", "-jar", "/home/app/bot.jar"]
