package interview_coach.servicetests;

import interview_coach.entities.Attempt;
import interview_coach.entities.CodeSubmission;
import interview_coach.entities.CodingChallenge;
import interview_coach.entities.Question;
import interview_coach.entities.Session;
import interview_coach.entities.SessionQuestion;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.GradingStatus;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.events.CodeSubmissionCreatedEvent;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.TestCaseRepository;
import interview_coach.entities.TestCase;
import interview_coach.services.core.JudgeService;
import interview_coach.services.core.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Covers the Phase 4 finalization hook (whatever happens to grading, the parent session's
 * finalizeIfGradingComplete must still run, via the finally block) and the actual Judge0
 * round-trip, via a RestClient bound to MockRestServiceServer rather than a live/mock Judge0
 * server - possible now that JudgeService takes its RestClient by constructor injection
 * (see Judge0Config) instead of building one inline.
 */
@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    @Mock
    private CodeSubmissionRepository codeSubmissionRepository;
    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private SessionService sessionService;

    private MockRestServiceServer mockServer;
    private JudgeService judgeService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        judgeService = new JudgeService(restClient, codeSubmissionRepository, testCaseRepository, attemptRepository, sessionService);
        ReflectionTestUtils.setField(judgeService, "judge0Url", "https://judge0.test");
        ReflectionTestUtils.setField(judgeService, "judge0ApiKey", "");
    }

    private CodeSubmission submissionWithTestCases(List<TestCase> testCases) {
        Session session = Session.builder().id(1L).status(SessionStatus.AWAITING_GRADING).build();
        CodingChallenge challenge = CodingChallenge.builder().id(1L).build();
        Question question = Question.builder().id(1L).score(20).questionType(QuestionType.CODING).codingChallenge(challenge).build();
        SessionQuestion sq = SessionQuestion.builder().id(1L).session(session).question(question).orderIndex(1).build();
        Attempt attempt = Attempt.builder().id(1L).sessionQuestion(sq).status(AttemptStatus.PENDING).build();
        CodeSubmission submission = CodeSubmission.builder().id(1L).attempt(attempt).sourceCode("code").status(GradingStatus.PENDING).build();

        when(codeSubmissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.findByCodingChallengeId(1L)).thenReturn(testCases);
        return submission;
    }

    @Test
    void gradeSubmission_noTestCases_marksFailedAndStillFinalizesSession() {
        CodeSubmission submission = submissionWithTestCases(List.of()); // empty list triggers the failure branch

        judgeService.gradeSubmission(new CodeSubmissionCreatedEvent(1L));

        assertThat(submission.getStatus()).isEqualTo(GradingStatus.FAILED);
        assertThat(submission.getAttempt().getStatus()).isEqualTo(AttemptStatus.FAILED);

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(sessionService).finalizeIfGradingComplete(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getId()).isEqualTo(1L);
    }

    @Test
    void gradeSubmission_compileError_scoresZeroInsteadOfFailingGrading() {
        TestCase tc = TestCase.builder().input("1").expectedOutput("2").build();
        CodeSubmission submission = submissionWithTestCases(List.of(tc));

        // Judge0 returns stdout: null (not an HTTP error) on a compile failure.
        mockServer.expect(requestTo("https://judge0.test/submissions?wait=true"))
                .andRespond(withSuccess("""
                        {"stdout": null, "compile_output": "error: cannot find symbol", "status": {"description": "Compilation Error"}}
                        """, MediaType.APPLICATION_JSON));

        judgeService.gradeSubmission(new CodeSubmissionCreatedEvent(1L));

        // Grading itself succeeded - the answer was just wrong (0 tests passed), not a FAILED
        // grading run - that's the bug this fixes: a null stdout used to throw and land here as
        // FAILED instead.
        assertThat(submission.getStatus()).isEqualTo(GradingStatus.COMPLETED);
        assertThat(submission.getPassedTestCount()).isEqualTo(0);
        assertThat(submission.getTotalTestCount()).isEqualTo(1);
        assertThat(submission.getExecutionOutput()).isEqualTo("error: cannot find symbol");
        assertThat(submission.getAttempt().getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(submission.getAttempt().getScore()).isEqualTo(0);

        mockServer.verify();
    }

    @Test
    void gradeSubmission_mixedPassFail_scoresPartialCredit() {
        TestCase tc1 = TestCase.builder().input("1").expectedOutput("2").build();
        TestCase tc2 = TestCase.builder().input("2").expectedOutput("4").build();
        CodeSubmission submission = submissionWithTestCases(List.of(tc1, tc2));

        mockServer.expect(requestTo("https://judge0.test/submissions?wait=true"))
                .andRespond(withSuccess("""
                        {"stdout": "2\\n", "status": {"description": "Accepted"}}
                        """, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("https://judge0.test/submissions?wait=true"))
                .andRespond(withSuccess("""
                        {"stdout": "5\\n", "status": {"description": "Wrong Answer"}}
                        """, MediaType.APPLICATION_JSON));

        judgeService.gradeSubmission(new CodeSubmissionCreatedEvent(1L));

        assertThat(submission.getStatus()).isEqualTo(GradingStatus.COMPLETED);
        assertThat(submission.getPassedTestCount()).isEqualTo(1);
        assertThat(submission.getTotalTestCount()).isEqualTo(2);
        assertThat(submission.getAttempt().getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(submission.getAttempt().getScore()).isEqualTo(10); // 1/2 x 20

        mockServer.verify();
    }
}
