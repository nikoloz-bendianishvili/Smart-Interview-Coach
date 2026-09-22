package interview_coach.services.core;


import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import interview_coach.entities.Attempt;
import interview_coach.entities.Question;
import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.GradingStatus;
import interview_coach.exceptions.VoiceAnswerNotFoundException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class AiGradingService {

    @Value("${ai.model}")
    private String model;

    private final AnthropicClient anthropicClient;
    private final VoiceAnswerRepository voiceAnswerRepository;
    private final AttemptRepository attemptRepository;

    private record GradeResult(double score, String feedback) {
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void gradeAnswer(Long voiceAnswerId) {
        VoiceAnswer voiceAnswer = voiceAnswerRepository.findById(voiceAnswerId)
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
            attemptRepository.save(attempt);

        } catch (Exception e) {
            voiceAnswer.setGradingStatus(GradingStatus.FAILED);
            voiceAnswerRepository.save(voiceAnswer);
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
