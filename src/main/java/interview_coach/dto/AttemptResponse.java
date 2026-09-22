package interview_coach.dto;

import interview_coach.enums.AttemptStatus;
import interview_coach.enums.GradingStatus;
import interview_coach.enums.QuestionType;

public record AttemptResponse(
        Long attemptId,
        Long sessionQuestionId,
        QuestionType questionType,
        AttemptStatus status,
        Integer score,
        Integer timeTakenSeconds,

        // MCQ only
        Boolean isCorrect,

        // CODING / OPEN_ENDED only
        GradingStatus gradingStatus,

        // CODING only
        Integer passedTestCount,
        Integer totalTestCount,

        // OPEN_ENDED only
        Double aiScore,
        String aiFeedback
) {}