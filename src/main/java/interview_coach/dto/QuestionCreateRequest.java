package interview_coach.dto;

import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QuestionCreateRequest(
        @NotNull Long topicId,
        @NotBlank String statement,
        @NotNull QuestionType questionType,
        @NotNull Difficulty difficulty,
        @NotNull @Positive Integer timeLimit,
        Integer score,
        String explanation,
        @Valid OptionCreateRequest option,
        @Valid CodingChallengeCreateRequest codingChallenge
) {}
