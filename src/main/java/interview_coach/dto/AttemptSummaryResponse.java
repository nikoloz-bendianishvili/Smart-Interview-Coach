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
        Integer timeTakenSeconds,
        String explanation,

        // MCQ only. correctOption is null while the session is still IN_PROGRESS, even though
        // this question has already been answered - kept simple/conservative rather than reasoning
        // per-question about whether revealing it early could matter.
        Integer selectedOption,
        Integer correctOption,

        // OPEN_ENDED only
        String textAnswer,
        String aiFeedback,

        // CODING only
        String sourceCode,
        Integer passedTestCount,
        Integer totalTestCount,
        String executionOutput
) {}
