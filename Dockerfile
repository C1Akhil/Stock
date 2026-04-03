FROM openjdk:17-jdk-slim
COPY target/nifty50-analyser-1.0.0.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]