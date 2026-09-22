package interview_coach.dto;

import interview_coach.enums.AttemptStatus;

public record GiveUpResponse(
        Long attemptId,
        Long sessionQuestionId,
        AttemptStatus status,
        String explanation
) {}