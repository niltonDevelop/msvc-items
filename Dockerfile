# Requiere libs-msvc-commons. Build desde el directorio raíz SpringCloud:
#   docker build -f msvc-items/Dockerfile -t msvc-items .
# Run:
#   docker run -p 8002:8002 msvc-items

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY libs-msvc-commons/pom.xml libs-msvc-commons/pom.xml
COPY libs-msvc-commons/src libs-msvc-commons/src
RUN mvn -B -f libs-msvc-commons/pom.xml install -DskipTests

COPY msvc-items/pom.xml msvc-items/pom.xml
COPY msvc-items/src msvc-items/src
RUN mvn -B -f msvc-items/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=docker
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring
COPY --from=build /workspace/msvc-items/target/*.jar app.jar
EXPOSE 8002
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
