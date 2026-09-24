package interview_coach.enums;

public enum AttemptStatus {
    PENDING,    // submitted, waiting on async grading (coding/open-ended only)
    GRADED,     // final score is set and correct
    SKIPPED,    // user gave up, no grading needed
    FAILED      // grading attempt errored (Judge0 down, AI API failed, etc.)
}
