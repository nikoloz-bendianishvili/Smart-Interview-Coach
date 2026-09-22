package interview_coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record OpenEndedAttemptRequest(
        @NotBlank String answerText,
        @NotNull @PositiveOrZero Integer timeTakenSeconds
) {}