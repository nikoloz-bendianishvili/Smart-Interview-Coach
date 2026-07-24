package interview_coach.services.core;

import interview_coach.entities.Attempt;
import interview_coach.entities.CodeSubmission;
import interview_coach.entities.TestCase;
import interview_coach.enums.GradingStatus;
import interview_coach.exceptions.CodeSubmissionNotFoundException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.TestCaseRepository;
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
public class JudgeService {

    @Value("${judge0.api.url}")
    private String judge0Url;

    @Value("${judge0.api.key}")
    private String judge0ApiKey; // if using a hosted/RapidAPI instance rather than self-hosted

    private final RestClient restClient = RestClient.create();
    private final CodeSubmissionRepository codeSubmissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final AttemptRepository attemptRepository;

    @Async
    @Transactional
    public void gradeSubmission(Long submissionId) {
        CodeSubmission submission = codeSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new CodeSubmissionNotFoundException("Code submission not found"));

        try {
            List<TestCase> testCases = testCaseRepository.findByCodingChallengeId(
                    submission.getAttempt().getSessionQuestion().getQuestion().getCodingChallenge().getId()
            );

            int passed = 0;
            for (TestCase tc : testCases) {
                String actualOutput = runOnJudge0(submission.getSourceCode(), tc.getInput());
                if (actualOutput.trim().equals(tc.getExpectedOutput().trim())) {
                    passed++;
                }
            }

            submission.setPassedTestCount(passed);
            submission.setTotalTestCount(testCases.size());
            submission.setStatus(GradingStatus.COMPLETED);
            codeSubmissionRepository.save(submission);

            Attempt attempt = submission.getAttempt();
            int score = (int) Math.round((double) passed / testCases.size() * attempt.getSessionQuestion().getQuestion().getScore());
            attempt.setScore(score);
            attemptRepository.save(attempt);
        } catch (Exception e) {
            submission.setStatus(GradingStatus.FAILED);
            codeSubmissionRepository.save(submission);
        }
    }

    private String runOnJudge0(String sourceCode, String input) {
        Map<String, Object> requestBody = Map.of(
                "source_code", sourceCode,
                "language_id", 62, // Java
                "stdin", input
        );

        Map<String, Object> response = restClient.post()
                .uri(judge0Url + "/submissions?wait=true")
                .header("X-RapidAPI-Key", judge0ApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        return (String) response.getOrDefault("stdout", "");
    }
}
