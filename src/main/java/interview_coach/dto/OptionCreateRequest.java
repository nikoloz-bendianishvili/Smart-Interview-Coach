package interview_coach.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OptionCreateRequest(
        @NotNull @Min(1) @Max(4) Integer correctOption,
        @NotBlank String option1,
        @NotBlank String option2,
        String option3,
        String option4
) {}
