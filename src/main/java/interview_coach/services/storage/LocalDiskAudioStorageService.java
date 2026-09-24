package interview_coach.services.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Development/default storage: writes voice-answer audio to a local directory
 * (app.audio.storage-dir). Kept behind AudioStorageService so a later move to S3/R2 is a new
 * implementation class rather than a rewrite of AttemptService/TranscriptionService.
 */
@Service
public class LocalDiskAudioStorageService implements AudioStorageService {

    @Value("${app.audio.storage-dir}")
    private String storageDir;

    @Override
    public String store(Long sessionQuestionId, MultipartFile audio) {
        try {
            Path dir = Path.of(storageDir);
            Files.createDirectories(dir);

            String fileName = "sq-" + sessionQuestionId + "-" + UUID.randomUUID() + extensionFor(audio.getContentType());
            Path target = dir.resolve(fileName).normalize();
            audio.transferTo(target);

            return fileName;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to store voice answer audio for session question " + sessionQuestionId, e);
        }
    }

    @Override
    public Resource load(String location) {
        return new FileSystemResource(Path.of(storageDir).resolve(location).normalize());
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            return "";
        }
        return switch (contentType) {
            case "audio/webm" -> ".webm";
            case "audio/ogg" -> ".ogg";
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/mp4", "audio/m4a" -> ".mp4";
            case "audio/wav", "audio/x-wav", "audio/wave" -> ".wav";
            default -> "";
        };
    }
}
