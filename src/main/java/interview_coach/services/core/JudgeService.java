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
import org.springframework.beans.factory.annotation.Qualifier;
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

    private static final int RAPIDAPI_LANGUAGE_ID_JAVA = 62;
    private static final String RAPIDAPI_HOST = "judge0-ce.p.rapidapi.com";
    private static final int MAX_EXECUTION_OUTPUT_LENGTH = 2000;

    @Value("${judge0.api.url}")
    private String judge0Url;

    @Value("${judge0.api.key}")
    private String judge0ApiKey; // if using a hosted/RapidAPI instance rather than self-hosted

    // Now one of two RestClient beans (see TranscriptionConfig) - by-type autowiring alone is
    // ambiguous, so this is qualified by bean name rather than left to rely on being the only one.
    @Qualifier("judge0RestClient")
    private final RestClient restClient;
    private final CodeSubmissionRepository codeSubmissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final AttemptRepository attemptRepository;
    private final SessionService sessionService;

    private record Judge0Result(String stdout, String compileOutput, String stderr, String statusDescription) {
    }

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
            String executionOutput = null;
            for (TestCase tc : testCases) {
                Judge0Result result = runOnJudge0(submission.getSourceCode(), tc.getInput());

                // A compile error or crash comes back with stdout: null rather than an HTTP
                // error - treat it as "this test case failed" rather than letting the null
                // reach .trim() and turn a real (gradeable) 0 into a grading FAILED.
                boolean testPassed = result.stdout() != null
                        && result.stdout().trim().equals(tc.getExpectedOutput().trim());

                if (testPassed) {
                    passed++;
                } else if (executionOutput == null) {
                    // Keep only the first failure's detail - a compile error or crash is worth
                    // showing the user; a plain wrong-output mismatch has nothing more specific
                    // to add than Judge0's own status description (e.g. "Wrong Answer").
                    executionOutput = truncate(firstNonBlank(
                            result.compileOutput(), result.stderr(), result.statusDescription()));
                }
            }

            submission.setPassedTestCount(passed);
            submission.setTotalTestCount(testCases.size());
            submission.setExecutionOutput(executionOutput);
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

    private Judge0Result runOnJudge0(String sourceCode, String input) {
        Map<String, Object> requestBody = Map.of(
                "source_code", sourceCode,
                "language_id", RAPIDAPI_LANGUAGE_ID_JAVA,
                "stdin", input
        );

        Map<?, ?> response = restClient.post()
                .uri(judge0Url + "/submissions?wait=true")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    // A self-hosted Judge0 instance needs neither header (and JUDGE0_API_KEY is
                    // blank by default) - only send them for a hosted/RapidAPI instance.
                    if (judge0ApiKey != null && !judge0ApiKey.isBlank()) {
                        headers.set("X-RapidAPI-Key", judge0ApiKey);
                        headers.set("X-RapidAPI-Host", RAPIDAPI_HOST);
                    }
                })
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return new Judge0Result(null, null, null, null);
        }

        String statusDescription = null;
        if (response.get("status") instanceof Map<?, ?> status) {
            Object description = status.get("description");
            statusDescription = description != null ? description.toString() : null;
        }

        return new Judge0Result(
                (String) response.get("stdout"),
                (String) response.get("compile_output"),
                (String) response.get("stderr"),
                statusDescription
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_EXECUTION_OUTPUT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_EXECUTION_OUTPUT_LENGTH);
    }
}
