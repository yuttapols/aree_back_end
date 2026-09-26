FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:25-jre
# รันด้วย user ที่ไม่ใช่ root
RUN groupadd --system app && useradd --system --gid app app && mkdir -p /data/storage && chown app:app /data/storage
USER app
WORKDIR /app
COPY --from=build /src/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
