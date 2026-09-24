package interview_coach.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The voice start/stop/submit sequence was violated: stop called with no active recording, or
 * submit called on a recording that was started but never stopped.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidVoiceRecordingStateException extends RuntimeException {
    public InvalidVoiceRecordingStateException(String message) {
        super(message);
    }
}
