package interview_coach.services.core;

import interview_coach.entities.Attempt;
import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.GradingStatus;
import interview_coach.events.VoiceAnswerCreatedEvent;
import interview_coach.events.VoiceAudioSubmittedEvent;
import interview_coach.exceptions.VoiceAnswerNotFoundException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import interview_coach.services.storage.AudioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Transcribes the audio uploaded via AttemptService.submitVoiceAttempt into
 * VoiceAnswer.audioTranscript, then publishes VoiceAnswerCreatedEvent so AiGradingService grades
 * the transcript exactly as it already grades a plain-text open-ended answer.
 *
 * Fired by AttemptService publishing VoiceAudioSubmittedEvent from inside the transaction that
 * creates the (PENDING) Attempt + VoiceAnswer row - same fire-and-forget-after-commit shape as
 * JudgeService/AiGradingService. Unlike those two, this listener's own body isn't @Transactional
 * (the external transcription call shouldn't hold a DB connection open) but the final
 * save-and-publish step on success is wrapped in one short transaction via TransactionTemplate,
 * because VoiceAnswerCreatedEvent must be published from within an actual transaction for
 * AiGradingService's own AFTER_COMMIT listener to fire on it at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionService {

    @Value("${transcription.model}")
    private String model;

    @Value("${transcription.api.url}")
    private String transcriptionUrl;

    @Value("${transcription.api.key}")
    private String apiKey;

    // One of two RestClient beans (see Judge0Config) - qualified by bean name rather than left
    // to by-type autowiring, which is ambiguous once there's more than one.
    @Qualifier("transcriptionRestClient")
    private final RestClient restClient;

    private final VoiceAnswerRepository voiceAnswerRepository;
    private final AttemptRepository attemptRepository;
    private final AudioStorageService audioStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final SessionService sessionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void transcribe(VoiceAudioSubmittedEvent event) {
        VoiceAnswer voiceAnswer = voiceAnswerRepository.findById(event.voiceAnswerId())
                .orElseThrow(() -> new VoiceAnswerNotFoundException("Voice answer not found"));

        try {
            String transcript = callTranscriptionApi(voiceAnswer);

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                voiceAnswer.setAudioTranscript(transcript);
                voiceAnswerRepository.save(voiceAnswer);

                Attempt attempt = voiceAnswer.getAttempt();
                attempt.setTextAnswer(transcript);
                attemptRepository.save(attempt);

                // Published from inside this transaction so AiGradingService's own
                // AFTER_COMMIT listener actually fires once it commits - never call the
                // @Async grading bean directly.
                eventPublisher.publishEvent(new VoiceAnswerCreatedEvent(voiceAnswer.getId()));
            });
        } catch (Exception e) {
            log.error("Failed to transcribe voice answer {}: {}", voiceAnswer.getId(), e.getMessage());
            voiceAnswer.setGradingStatus(GradingStatus.FAILED);
            voiceAnswerRepository.save(voiceAnswer);

            Attempt attempt = voiceAnswer.getAttempt();
            attempt.setStatus(AttemptStatus.FAILED);
            attemptRepository.save(attempt);

            // AiGradingService is never reached on this path (no VoiceAnswerCreatedEvent was
            // published), so its own finalizeIfGradingComplete never runs either - do it here
            // instead. Best-effort, same as JudgeService/AiGradingService's finally blocks;
            // SessionSweepService is the real backstop either way.
            sessionService.finalizeIfGradingComplete(attempt.getSessionQuestion().getSession());
        }
    }

    private String callTranscriptionApi(VoiceAnswer voiceAnswer) {
        Resource audio = audioStorageService.load(voiceAnswer.getAudioFileUrl());

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", audio);
        parts.add("model", model);

        Map<?, ?> response = restClient.post()
                .uri(transcriptionUrl + "/audio/transcriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("text") == null) {
            throw new IllegalStateException("No transcript text in transcription response");
        }

        return response.get("text").toString();
    }
}
