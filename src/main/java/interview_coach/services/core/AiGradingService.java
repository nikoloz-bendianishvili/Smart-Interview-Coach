package interview_coach.services.core;


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
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiGradingService {

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.url}")
    private String apiUrl; // e.g. Anthropic or OpenAI endpoint

    private final RestClient restClient = RestClient.create();
    private final VoiceAnswerRepository voiceAnswerRepository;
    private final AttemptRepository attemptRepository;

    @Async
    @Transactional
    public void gradeAnswer(Long voiceAnswerId) {
        VoiceAnswer voiceAnswer = voiceAnswerRepository.findById(voiceAnswerId)
                .orElseThrow(() -> new VoiceAnswerNotFoundException("Voice answer not found"));

        try {
            Question question = voiceAnswer.getAttempt().getSessionQuestion().getQuestion();
            String modelAnswer = question.getExplanation();
            String userAnswer = voiceAnswer.getAudioTranscript();

            Map<String, Object> aiResult = callAiApi(question.getStatement(), modelAnswer, userAnswer);

            double aiScore = (double) aiResult.get("score");
            String feedback = (String) aiResult.get("feedback");

            voiceAnswer.setAiScore(aiScore);
            voiceAnswer.setAiFeedback(feedback);
            voiceAnswer.setGradingStatus(GradingStatus.COMPLETED);
            voiceAnswerRepository.save(voiceAnswer);

            Attempt attempt = voiceAnswer.getAttempt();
            int scaledScore = (int) Math.round((aiScore / 10.0) * question.getScore());
            attempt.setScore(scaledScore);
            attemptRepository.save(attempt);

        } catch (Exception e) {
            voiceAnswer.setGradingStatus(GradingStatus.FAILED);
            voiceAnswerRepository.save(voiceAnswer);
        }
    }


    private Map<String, Object> callAiApi(String question, String modelAnswer, String userAnswer) {
        String prompt = """
            Question: %s
            Ideal answer: %s
            User's answer: %s
            
            Score the user's answer from 0-10 based on how well it covers the key ideas in the ideal answer.
            Respond ONLY in JSON format: {"score": <number>, "feedback": "<short constructive feedback>"}
            """.formatted(question, modelAnswer, userAnswer);

        Map<String, Object> requestBody = Map.of(
                "model", "claude-...", // whichever model/provider you're using
                "max_tokens", 300,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        Map<String, Object> response = restClient.post()
                .uri(apiUrl)
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        // extract and parse the AI's JSON response text into a score/feedback map
        return parseAiResponse(response);
    }
}
