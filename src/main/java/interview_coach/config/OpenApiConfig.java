package interview_coach.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a browsable API at /swagger-ui.html (docs at /v3/api-docs).
 * The "bearerAuth" scheme wires up the Authorize button in Swagger UI so
 * the whole login -> authorize -> call flow can be driven from the browser
 * with no separate HTTP client.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Interview Coach API",
                version = "v1",
                description = "REST API for a mock-interview practice platform: MCQ, coding and " +
                        "open-ended sessions with automated (Judge0 / AI) grading. " +
                        "Log in via /api/auth/login, then paste the returned token into Authorize below."
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
