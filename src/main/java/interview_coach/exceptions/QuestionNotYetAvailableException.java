package interview_coach.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A REAL_INTERVIEW session enforces strict in-order answering - only the current (lowest
 * orderIndex, not-yet-attempted) question can be submitted or given up. Thrown when a client
 * tries to jump ahead to a later question before the current one has been dealt with.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class QuestionNotYetAvailableException extends RuntimeException {
    public QuestionNotYetAvailableException(String message) {
        super(message);
    }
}
