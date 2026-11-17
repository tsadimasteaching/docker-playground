# ---------------------------------------------------------------------------
# Dockerfile — the "naive" version (step 1)
#
# It works, but it has several problems we fix in Dockerfile.multistage:
#   * the final image contains Maven, the full JDK, the source code and the
#     ~/.m2 cache  -> it is HUGE
#   * the app runs as root inside the container
#   * every source change re-downloads all Maven dependencies
#     (COPY . . invalidates the cache before `mvn package`)
#
# Build:  docker build -t tickets-backend:naive .
# Run:    docker run --rm --network tickets-net -e DB_HOST=tickets-db -p 8080:8080 tickets-backend:naive
#         (needs the PostgreSQL container "tickets-db" on network "tickets-net", see README)
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25

WORKDIR /app

COPY . .

RUN mvn -B package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/app.jar"]
