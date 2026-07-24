package interview_coach.exceptions;

public class SessionQuestionNotFoundException extends RuntimeException {
    public SessionQuestionNotFoundException(String message) {
        super(message);
    }
}
