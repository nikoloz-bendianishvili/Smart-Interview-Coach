package interview_coach.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class QuestionTypeMismatchException extends RuntimeException {
    public QuestionTypeMismatchException(String message) {
        super(message);
    }
}