FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build /workspace/target/sms-verification-gateway.jar /app/app.jar

USER app
EXPOSE 10000

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
