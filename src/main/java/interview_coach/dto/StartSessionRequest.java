package interview_coach.dto;

import interview_coach.enums.InteractionMode;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionType;

public record StartSessionRequest(
        SessionType sessionType,
        Long topicId,
        QuestionType questionType,
        InteractionMode interactionMode,
        Integer numOfQuestions,
        Integer timeLimitMinutes
) {}
