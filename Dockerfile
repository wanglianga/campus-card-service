FROM openjdk:17-jdk-slim AS build
RUN apt-get update && apt-get install -y curl unzip && rm -rf /var/lib/apt/lists/*
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

FROM openjdk:17-jre-slim
WORKDIR /app
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --from=build /app/build/libs/*-fat.jar app.jar
RUN mkdir -p /app/data && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
