FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/registration-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]