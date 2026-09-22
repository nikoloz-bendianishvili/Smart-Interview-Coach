package interview_coach.dto;

import interview_coach.enums.Role;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String webName,
        String email,
        Role role,
        String targetRole,
        boolean isBanned,
        boolean isVerified,
        LocalDateTime banExpiration,
        LocalDateTime createdAt
) {}
