# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Cache dependency resolution before copying source
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
COPY config ./config
RUN mvn --batch-mode --no-transfer-progress clean package -DskipTests


# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy AS runtime

RUN groupadd --system nexus \
    && useradd --system --gid nexus --no-create-home nexus

WORKDIR /app

COPY --from=build --chown=nexus:nexus /workspace/target/*.jar app.jar

# O Notify usa NOTIFY_PORT (default 8082). Discloud/K8s esperam 8080,
# então o default dentro do container é 8080 (sobrescrevível via env).
ENV NOTIFY_PORT=8080
EXPOSE 8080

USER nexus

ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xshare:auto", "-jar", "/app/app.jar"]
