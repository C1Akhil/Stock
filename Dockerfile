# Step 1: Build
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Step 2: Run
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Debug: list files (important)
COPY --from=build /app/target /app/target

RUN ls /app/target

# Copy exact jar
RUN cp /app/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]