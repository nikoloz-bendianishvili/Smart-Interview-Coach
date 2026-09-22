package interview_coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CodeAttemptRequest(
        @NotBlank String sourceCode,
        @NotNull @PositiveOrZero Integer timeTakenSeconds
) {}