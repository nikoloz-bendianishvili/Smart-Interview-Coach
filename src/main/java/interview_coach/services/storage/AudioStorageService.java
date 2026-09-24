package interview_coach.services.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Persists uploaded voice-answer audio and hands back a location string that {@link #load}
 * can later resolve back to the file - stored as-is on VoiceAnswer.audioFileUrl. Kept as an
 * interface so a later swap to S3/R2 (see CLAUDE.md's outstanding items) is a new implementation
 * class, not a rewrite of every caller.
 */
public interface AudioStorageService {

    /**
     * Stores the given audio file for a voice answer on {@code sessionQuestionId} and returns
     * a location that identifies it for a later {@link #load}.
     */
    String store(Long sessionQuestionId, MultipartFile audio);

    /**
     * Loads a previously stored file back by the location {@link #store} returned.
     */
    Resource load(String location);
}
