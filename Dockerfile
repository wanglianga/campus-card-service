FROM eclipse-temurin:17-jdk-alpine AS build
RUN apk add --no-cache curl unzip bash
WORKDIR /tmp
RUN curl -sL https://services.gradle.org/distributions/gradle-8.5-bin.zip -o gradle.zip && \
    unzip -q gradle.zip && \
    mv gradle-8.5 /opt/gradle && \
    ln -s /opt/gradle/bin/gradle /usr/local/bin/gradle && \
    rm gradle.zip
WORKDIR /app
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY src src
RUN gradle buildFatJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /app/build/libs/*-fat.jar app.jar
RUN mkdir -p /app/data && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
