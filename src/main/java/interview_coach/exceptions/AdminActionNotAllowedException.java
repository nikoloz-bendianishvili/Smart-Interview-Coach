package interview_coach.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AdminActionNotAllowedException extends RuntimeException {
    public AdminActionNotAllowedException(String message) {
        super(message);
    }
}
