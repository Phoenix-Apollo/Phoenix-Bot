FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /home/app

COPY pom.xml ./
COPY src ./src
COPY data ./data

RUN mvn clean package

FROM eclipse-temurin:21-jre
WORKDIR /home/app

RUN useradd -m -u 1000 appuser

COPY --from=build /home/app/target/*-shaded.jar /home/app/bot.jar
COPY --from=build /home/app/data /home/app/data

RUN chown -R appuser:appuser /home/app
USER appuser

ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /home/app/bot.jar"]
