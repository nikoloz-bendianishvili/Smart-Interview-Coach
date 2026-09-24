package interview_coach.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CodingChallengeNotFound extends RuntimeException {
    public CodingChallengeNotFound(String message) {
        super(message);
    }
}
