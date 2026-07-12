FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S gadang && adduser -S gadang -G gadang \
    && mkdir -p /app/uploads && chown -R gadang:gadang /app
COPY --from=build --chown=gadang:gadang /workspace/target/*.jar app.jar
USER gadang
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
