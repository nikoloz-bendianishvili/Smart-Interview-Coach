package interview_coach.dto;

import jakarta.validation.constraints.NotBlank;

public record TestCaseCreateRequest(
        @NotBlank String input,
        @NotBlank String expectedOutput,
        boolean isHidden
) {}
