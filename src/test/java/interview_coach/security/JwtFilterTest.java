package interview_coach.security;

import interview_coach.InterviewCoachApplication;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the JwtFilter bug where an expired or malformed token
 * bypassed JwtService.isTokenValid's try/catch entirely: extractEmail()
 * (called directly, outside any try/catch) threw ExpiredJwtException /
 * MalformedJwtException straight out of doFilterInternal, turning every
 * expired login into an unhandled 500 instead of a clean 401/403.
 * <p>
 * Loads the real SecurityConfig + JwtFilter (not a @WebMvcTest slice, which
 * excludes JwtFilter entirely) so the fix is exercised end-to-end. Both
 * tokens leave the security context unauthenticated, so the request is
 * rejected by Spring Security's own authorization check before reaching any
 * controller - same as SecurityConfig's documented anonymous-request
 * behavior (no AuthenticationEntryPoint configured, so 403 rather than 401).
 */
@SpringBootTest(classes = InterviewCoachApplication.class)
@AutoConfigureMockMvc
class JwtFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
    }

    @Test
    void expiredToken_doesNotReturn500() throws Exception {
        String expiredToken = Jwts.builder()
                .subject("someone@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 2_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000)) // already expired
                .signWith(signingKey())
                .compact();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isForbidden()); // not 500 - see class Javadoc for why 403
    }

    @Test
    void malformedToken_doesNotReturn500() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isForbidden()); // not 500
    }
}
