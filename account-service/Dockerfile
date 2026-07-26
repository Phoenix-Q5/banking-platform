FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY banking-events/pom.xml banking-events/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY account-service/pom.xml account-service/pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY ops-agent/pom.xml ops-agent/pom.xml
COPY ai-agent/pom.xml ai-agent/pom.xml
COPY customer-service/pom.xml customer-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY card-service/pom.xml card-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY audit-service/pom.xml audit-service/pom.xml
COPY loan-service/pom.xml loan-service/pom.xml

RUN mvn -q -pl account-service -am dependency:go-offline -DskipTests || true

COPY banking-events banking-events
COPY account-service account-service
RUN mvn -q -pl account-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/account-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
