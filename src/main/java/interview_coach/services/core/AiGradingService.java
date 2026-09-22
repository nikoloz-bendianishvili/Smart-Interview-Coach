package interview_coach.services.core;


import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import interview_coach.entities.Attempt;
import interview_coach.entities.Question;
import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.GradingStatus;
import interview_coach.events.VoiceAnswerCreatedEvent;
import interview_coach.exceptions.VoiceAnswerNotFoundException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGradingService {

    @Value("${ai.model}")
    private String model;

    private final AnthropicClient anthropicClient;
    private final VoiceAnswerRepository voiceAnswerRepository;
    private final AttemptRepository attemptRepository;
    private final SessionService sessionService;

    private record GradeResult(double score, String feedback) {
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void gradeAnswer(VoiceAnswerCreatedEvent event) {
        VoiceAnswer voiceAnswer = voiceAnswerRepository.findById(event.voiceAnswerId())
                .orElseThrow(() -> new VoiceAnswerNotFoundException("Voice answer not found"));

        try {
            Question question = voiceAnswer.getAttempt().getSessionQuestion().getQuestion();
            String modelAnswer = question.getExplanation();
            String userAnswer = voiceAnswer.getAudioTranscript();

            GradeResult result = callAiApi(question.getStatement(), modelAnswer, userAnswer);

            voiceAnswer.setAiScore(result.score());
            voiceAnswer.setAiFeedback(result.feedback());
            voiceAnswer.setGradingStatus(GradingStatus.COMPLETED);
            voiceAnswerRepository.save(voiceAnswer);

            Attempt attempt = voiceAnswer.getAttempt();
            int scaledScore = (int) Math.round((result.score() / 10.0) * question.getScore());
            attempt.setScore(scaledScore);
            attempt.setStatus(AttemptStatus.GRADED);
            attemptRepository.save(attempt);

        } catch (Exception e) {
            log.error("Failed to grade voice answer {}: {}", voiceAnswer.getId(), e.getMessage());
            voiceAnswer.setGradingStatus(GradingStatus.FAILED);
            voiceAnswerRepository.save(voiceAnswer);

            Attempt attempt = voiceAnswer.getAttempt();
            attempt.setStatus(AttemptStatus.FAILED);
            attemptRepository.save(attempt);
        } finally {
            // See JudgeService.gradeSubmission's identical finally block for why this is
            // best-effort here and backstopped by SessionSweepService's sweep.
            sessionService.finalizeIfGradingComplete(voiceAnswer.getAttempt().getSessionQuestion().getSession());
        }
    }


    private GradeResult callAiApi(String question, String modelAnswer, String userAnswer) {
        String prompt = """
            Question: %s
            Ideal answer: %s
            User's answer: %s

            Score the user's answer from 0-10 based on how well it covers the key ideas in the ideal answer.
            """.formatted(question, modelAnswer, userAnswer);

        StructuredMessageCreateParams<GradeResult> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .outputConfig(GradeResult.class)
                .addUserMessage(prompt)
                .build();

        return anthropicClient.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(textBlock -> textBlock.text())
                .orElseThrow(() -> new IllegalStateException("No text content in AI grading response"));
    }
}
