package interview_coach.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class SessionAccessDeniedException extends RuntimeException {
    public SessionAccessDeniedException(String message) {
        super(message);
    }
}