package interview_coach.dto;

import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;

import java.time.LocalDateTime;
import java.util.List;

public record SessionSummaryResponse(
        Long sessionId,
        SessionType sessionType,
        SessionStatus status,
        Integer totalScore,
        int maxScore,
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<AttemptSummaryResponse> results
) {}