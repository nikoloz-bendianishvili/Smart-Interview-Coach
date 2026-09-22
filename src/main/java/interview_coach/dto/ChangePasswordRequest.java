package interview_coach.dto;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) {}
