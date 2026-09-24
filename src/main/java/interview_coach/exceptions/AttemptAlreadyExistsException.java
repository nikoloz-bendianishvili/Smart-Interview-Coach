package interview_coach.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AttemptAlreadyExistsException extends RuntimeException {
    public AttemptAlreadyExistsException(String message) {
        super(message);
    }
}