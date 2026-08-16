FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY build/libs/*.jar /app/libs/
RUN set -eux; \
    jar_file="$(find /app/libs -type f -name '*.jar' ! -name '*plain.jar' | head -n 1)"; \
    cp "$jar_file" /app/app.jar; \
    rm -rf /app/libs

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
