package interview_coach.dto;

public record UserUpdateDTO(
        String firstName,
        String lastName,
        String webName,
        String email,
        String targetRole,
        String password
) {}
