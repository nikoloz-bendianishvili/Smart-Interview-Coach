package interview_coach.dto;

import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;

import java.time.LocalDateTime;

/**
 * One row of a user's session history list (GET /api/sessions/me) - lighter than
 * SessionSummaryResponse since a list view doesn't need the per-question breakdown.
 */
public record SessionHistoryItemResponse(
        Long sessionId,
        SessionType sessionType,
        SessionStatus status,
        Integer totalScore,
        int maxScore,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean endedByTimeout
) {}
