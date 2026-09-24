package interview_coach.dto;

import java.time.LocalDateTime;

/**
 * expiresAt == null means a permanent ban (matches JwtFilter.isCurrentlyBanned semantics).
 */
public record BanRequest(
        LocalDateTime expiresAt
) {}
