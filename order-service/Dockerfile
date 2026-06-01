# =========================================================================
# STAGE 1: BUILD JAR
# =========================================================================
FROM maven:3.8.5-openjdk-17 AS builder
WORKDIR /app

# Cache dependency layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source files and compile
COPY src ./src
RUN mvn package -DskipTests -B

# =========================================================================
# STAGE 2: LEAN RUNTIME
# =========================================================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/target/order-service-1.0.0.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
