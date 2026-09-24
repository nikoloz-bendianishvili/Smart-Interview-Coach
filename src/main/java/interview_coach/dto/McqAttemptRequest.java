package interview_coach.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record McqAttemptRequest(
        @NotNull @Min(1) @Max(4) Integer selectedOption,
        @NotNull @PositiveOrZero Integer timeTakenSeconds
) {}