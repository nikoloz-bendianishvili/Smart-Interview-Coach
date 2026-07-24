package interview_coach.security.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String firstName;
    private String lastName;
    private String webName;
    private String email;
    private String password;
    private String targetRole;
}
