package interview_coach.dto;

import interview_coach.enums.AttemptStatus;
import interview_coach.enums.QuestionType;

public record AttemptSummaryResponse(
        Long sessionQuestionId,
        int orderIndex,
        QuestionType questionType,
        String statement,
        AttemptStatus status,
        Integer score,
        Integer maxScore,
        String explanation,
        String aiFeedback
) {}