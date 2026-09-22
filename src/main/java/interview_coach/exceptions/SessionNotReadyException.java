package interview_coach.exceptions;

public class SessionNotReadyException extends RuntimeException {
    public SessionNotReadyException(String message) {
        super(message);
    }
}
