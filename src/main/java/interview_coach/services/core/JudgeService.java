package interview_coach.services.core;

import interview_coach.entities.Attempt;
import interview_coach.entities.CodeSubmission;
import interview_coach.entities.TestCase;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.GradingStatus;
import interview_coach.events.CodeSubmissionCreatedEvent;
import interview_coach.exceptions.CodeSubmissionNotFoundException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
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
    private final SessionService sessionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void gradeSubmission(CodeSubmissionCreatedEvent event) {
        CodeSubmission submission = codeSubmissionRepository.findById(event.submissionId())
                .orElseThrow(() -> new CodeSubmissionNotFoundException("Code submission not found"));

        try {
            List<TestCase> testCases = testCaseRepository.findByCodingChallengeId(
                    submission.getAttempt().getSessionQuestion().getQuestion().getCodingChallenge().getId()
            );

            if (testCases.isEmpty()) {
                throw new IllegalStateException("No test cases found for coding challenge");
            }

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
            attempt.setStatus(AttemptStatus.GRADED);
            attemptRepository.save(attempt);
        } catch (Exception e) {
            log.error("Failed to grade code submission {}: {}", submission.getId(), e.getMessage());
            submission.setStatus(GradingStatus.FAILED);
            codeSubmissionRepository.save(submission);

            Attempt attempt = submission.getAttempt();
            attempt.setStatus(AttemptStatus.FAILED);
            attemptRepository.save(attempt);
        } finally {
            // Best-effort immediate finalization - if this session was AWAITING_GRADING and
            // this was its last pending attempt, flip it to COMPLETED now rather than waiting
            // for the next sweep tick. Runs on both success and failure alike, since either way
            // this attempt has left PENDING. See SessionSweepService's Javadoc for why this
            // can't be relied on as the ONLY finalization path (a same-tick-race with another
            // grader can make both miss it).
            sessionService.finalizeIfGradingComplete(submission.getAttempt().getSessionQuestion().getSession());
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
