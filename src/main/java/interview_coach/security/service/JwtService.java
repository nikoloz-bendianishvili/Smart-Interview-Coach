package interview_coach.security.service;

import interview_coach.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("webName", user.getWebName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isTokenValid(String token, String expectedEmail) {
        try {
            return !isTokenExpired(token) && extractEmail(token).equals(expectedEmail);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            return extractEveryClaim(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private Claims extractEveryClaim(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractEveryClaim(token).getSubject();
    }

    public String extractRole(String token) {
        return extractEveryClaim(token).get("role", String.class);
    }

    public String extractWebName(String token) {
        return extractEveryClaim(token).get("webName", String.class);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
