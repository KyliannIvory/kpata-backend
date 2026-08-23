FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

COPY pom.xml .
COPY mvnw .
COPY .mvn/ .mvn/

RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B

COPY src/ src/

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /opt/app/

COPY --from=build /app/target/kpata-backend.jar app.jar

RUN chown appuser:appgroup app.jar

EXPOSE 8080

USER appuser:appgroup

ENTRYPOINT ["java", "-jar", "app.jar"]