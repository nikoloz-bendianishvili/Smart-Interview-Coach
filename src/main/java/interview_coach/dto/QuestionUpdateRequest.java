package interview_coach.dto;

import interview_coach.enums.Difficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QuestionUpdateRequest(
        @NotNull Long topicId,
        @NotBlank String statement,
        @NotNull Difficulty difficulty,
        @NotNull @Positive Integer timeLimit,
        String explanation
) {}
