package interview_coach.services.email;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${sender}")
    private String senderMail;

    private final RestClient restClient = RestClient.create();

    @Async
    public void sendVerificationEmail(String email, String verificationToken) {
        Map<String, Object> requestBody = getEmail(email, verificationToken);

        try {
            restClient.post()
                    .uri("https://api.brevo.com/v3/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Verification email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", email, e.getMessage());
        }
    }

    private @NonNull Map<String, Object> getEmail(String email, String verificationToken) {
        String url = frontendUrl + "/verify?token=" + verificationToken;
        return Map.of(
                "sender",Map.of("name", "Interview_Coach", "email", senderMail),
                "to", List.of(Map.of("email", email)),
                "subject", "Email verification - Interview_Coach",
                "htmlContent", "<h2>Verify your email</h2>" +
                        "<p>" +
                        "Please click the link below to verify your account:" +
                        "</p>" +
                        "<a href='" + url + "'>" +
                        "Click here to verify" +
                        "</a>"

        );
    }
}
