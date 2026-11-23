import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Tiny health check for the distroless image (Dockerfile.distroless).
 *
 * Distroless images have no shell, no wget and no curl, so the HEALTHCHECK
 * cannot run "wget -qO- http://localhost:8080/actuator/health". Instead it runs
 * this class with the JVM that is already in the image:
 *
 *     java -cp /app/healthcheck HealthCheck
 *
 * Exit code 0 = healthy, 1 = unhealthy (that is all Docker looks at).
 */
public class HealthCheck {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "http://localhost:8080/actuator/health";
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            System.exit(status == 200 ? 0 : 1);
        } catch (Exception e) {
            System.err.println("health check failed: " + e);
            System.exit(1);
        }
    }
}
