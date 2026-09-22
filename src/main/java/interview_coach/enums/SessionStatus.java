package interview_coach.enums;

public enum SessionStatus {
    IN_PROGRESS,
    /** Closed (deadline passed, or user called complete()) but one or more attempts are still
     *  being graded asynchronously. Transitions to COMPLETED once none are PENDING. */
    AWAITING_GRADING,
    COMPLETED,
    ABANDONED
}
