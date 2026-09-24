package interview_coach.dto;

import interview_coach.enums.SessionType;

import java.util.List;

public record SessionStartResponse(
        Long sessionId,
        SessionType sessionType,
        int totalQuestions,
        List<SessionQuestionResponse> questions
) {}
