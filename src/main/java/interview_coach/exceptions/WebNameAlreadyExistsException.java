package interview_coach.exceptions;

public class WebNameAlreadyExistsException extends RuntimeException {
    public WebNameAlreadyExistsException(String message) {
        super(message);
    }
}
