FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY target/nifty50-analyser-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app.jar"]