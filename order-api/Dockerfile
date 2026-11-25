# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER app
EXPOSE 8400
# SERVICE_IP = IP do container: é o endereço que o serviço registra no Consul
ENTRYPOINT ["sh", "-c", "export SERVICE_IP=$(awk 'END{print $1}' /etc/hosts); exec java -XX:MaxRAMPercentage=75 -jar app.jar"]
