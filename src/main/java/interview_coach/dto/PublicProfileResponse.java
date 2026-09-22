package interview_coach.dto;

import java.time.LocalDateTime;

public record PublicProfileResponse(
        String webName,
        String targetRole,
        LocalDateTime createdAt
) {}
