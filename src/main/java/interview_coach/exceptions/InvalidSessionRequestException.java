package interview_coach.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A session-start request is malformed for its SessionType - e.g. REAL_INTERVIEW with no
 * timeLimitMinutes, or FREE_MOCK with both/neither of numOfQuestions and timeLimitMinutes.
 * A client mistake, not a server error.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidSessionRequestException extends RuntimeException {
    public InvalidSessionRequestException(String message) {
        super(message);
    }
}
