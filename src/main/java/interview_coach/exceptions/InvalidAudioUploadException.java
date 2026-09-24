package interview_coach.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The uploaded voice/submit file is missing, empty, or not an audio content type - a client
 * mistake, not a recording-sequence violation (see InvalidVoiceRecordingStateException for that).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAudioUploadException extends RuntimeException {
    public InvalidAudioUploadException(String message) {
        super(message);
    }
}
