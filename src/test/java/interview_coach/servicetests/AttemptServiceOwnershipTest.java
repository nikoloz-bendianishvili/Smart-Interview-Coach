package interview_coach.servicetests;

import interview_coach.entities.*;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.exceptions.AttemptAlreadyExistsException;
import interview_coach.exceptions.QuestionNotYetAvailableException;
import interview_coach.exceptions.QuestionTypeMismatchException;
import interview_coach.exceptions.SessionAccessDeniedException;
import interview_coach.exceptions.SessionAlreadyCompletedException;
import interview_coach.repositories.*;
import interview_coach.services.core.AttemptService;
import interview_coach.services.core.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifies AttemptService's ownership, session-state, duplicate-attempt and
 * question-type guards, which sit in front of every submit/give-up/lookup call.
 */
@ExtendWith(MockitoExtension.class)
class AttemptServiceOwnershipTest {

    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private OptionRepository optionRepository;
    @Mock
    private SessionQuestionRepository sessionQuestionRepository;
    @Mock
    private CodeSubmissionRepository codeSubmissionRepository;
    @Mock
    private VoiceAnswerRepository voiceAnswerRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SessionService sessionService;

    private AttemptService attemptService;

    private User owner;
    private User intruder;

    @BeforeEach
    void setUp() {
        attemptService = new AttemptService(
                attemptRepository,
                optionRepository,
                sessionQuestionRepository,
                codeSubmissionRepository,
                voiceAnswerRepository,
                eventPublisher,
                sessionService,
                Clock.systemDefaultZone()
        );
        // closeIfExpired's mocked default (false) means "not expired" - preserves this test
        // class's existing IN_PROGRESS/COMPLETED fixtures untouched.

        owner = User.builder().id(1L).build();
        intruder = User.builder().id(2L).build();

        // Not every test reaches the existsBySessionQuestionId check (some fail earlier),
        // so this default stub is lenient.
        lenient().when(attemptRepository.existsBySessionQuestionId(anyLong())).thenReturn(false);
    }

    private SessionQuestion sessionQuestionOwnedBy(User user, SessionStatus status, QuestionType questionType) {
        Session session = Session.builder().id(1L).user(user).status(status).build();
        Question question = Question.builder().id(1L).score(10).questionType(questionType).build();
        return SessionQuestion.builder().id(1L).session(session).question(question).orderIndex(1).build();
    }

    @Test
    void submitMCQAttempt_forAnotherUsersSession_throwsSessionAccessDenied() {
        when(sessionQuestionRepository.findById(1L))
                .thenReturn(Optional.of(sessionQuestionOwnedBy(owner, SessionStatus.IN_PROGRESS, QuestionType.MCQ)));

        assertThatThrownBy(() -> attemptService.submitMCQAttempt(1L, intruder, 1, 30))
                .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    void submitCodingAttempt_onCompletedSession_throwsSessionAlreadyCompleted() {
        when(sessionQuestionRepository.findById(1L))
                .thenReturn(Optional.of(sessionQuestionOwnedBy(owner, SessionStatus.COMPLETED, QuestionType.CODING)));

        assertThatThrownBy(() -> attemptService.submitCodingAttempt(1L, owner, "code", 60))
                .isInstanceOf(SessionAlreadyCompletedException.class);
    }

    @Test
    void submitOpenEndedAttempt_alreadyAnswered_throwsAttemptAlreadyExists() {
        when(sessionQuestionRepository.findById(1L))
                .thenReturn(Optional.of(sessionQuestionOwnedBy(owner, SessionStatus.IN_PROGRESS, QuestionType.OPEN_ENDED)));
        when(attemptRepository.existsBySessionQuestionId(1L)).thenReturn(true);

        assertThatThrownBy(() -> attemptService.submitOpenEndedAttempt(1L, owner, "answer", 45))
                .isInstanceOf(AttemptAlreadyExistsException.class);
    }

    @Test
    void submitMCQAttempt_onCodingQuestion_throwsQuestionTypeMismatch() {
        when(sessionQuestionRepository.findById(1L))
                .thenReturn(Optional.of(sessionQuestionOwnedBy(owner, SessionStatus.IN_PROGRESS, QuestionType.CODING)));

        assertThatThrownBy(() -> attemptService.submitMCQAttempt(1L, owner, 1, 30))
                .isInstanceOf(QuestionTypeMismatchException.class);
    }

    @Test
    void giveUpAttempt_skipsTypeCheck_onAnyQuestionType() {
        SessionQuestion sq = sessionQuestionOwnedBy(owner, SessionStatus.IN_PROGRESS, QuestionType.CODING);
        sq.getQuestion().setExplanation("explanation");
        when(sessionQuestionRepository.findById(1L)).thenReturn(Optional.of(sq));
        when(attemptRepository.save(org.mockito.ArgumentMatchers.any(Attempt.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // FREE_MOCK / REAL_INTERVIEW path avoids needing a CodingChallenge stub.
        sq.getSession().setSessionType(interview_coach.enums.SessionType.FREE_MOCK);

        AttemptService.GiveUpResult result = attemptService.giveUpAttempt(1L, owner);

        org.assertj.core.api.Assertions.assertThat(result.explanation())
                .isEqualTo("Answer recorded. You'll see the explanation when the session ends.");
    }

    @Test
    void submitMCQAttempt_onRealInterview_whenThisIsTheCurrentQuestion_succeeds() {
        SessionQuestion sq = sessionQuestionOwnedBy(owner, SessionStatus.IN_PROGRESS, QuestionType.MCQ);
        sq.getSession().setSessionType(SessionType.REAL_INTERVIEW);
        when(sessionQuestionRepository.findById(1L)).thenReturn(Optional.of(sq));
        when(sessionQuestionRepository.findFirstBySession_IdAndAttemptIsNullOrderByOrderIndexAsc(1L))
                .thenReturn(Optional.of(sq)); // this question IS the current one
        when(optionRepository.findByQuestionId(1L)).thenReturn(Optional.of(
                Option.builder().option1("A").option2("B").correctOption(1).build()));
        when(attemptRepository.save(org.mockito.ArgumentMatchers.any(Attempt.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Attempt attempt = attemptService.submitMCQAttempt(1L, owner, 1, 30);

        org.assertj.core.api.Assertions.assertThat(attempt.getIsCorrect()).isTrue();
    }

    @Test
    void submitMCQAttempt_onRealInterview_whenAnotherQuestionIsCurrent_throwsQuestionNotYetAvailable() {
        SessionQuestion sq = sessionQuestionOwnedBy(owner, SessionStatus.IN_PROGRESS, QuestionType.MCQ);
        sq.getSession().setSessionType(SessionType.REAL_INTERVIEW);
        SessionQuestion earlierUnansweredQuestion = SessionQuestion.builder().id(999L).orderIndex(1).build();
        when(sessionQuestionRepository.findById(1L)).thenReturn(Optional.of(sq));
        when(sessionQuestionRepository.findFirstBySession_IdAndAttemptIsNullOrderByOrderIndexAsc(1L))
                .thenReturn(Optional.of(earlierUnansweredQuestion)); // a DIFFERENT question is current

        assertThatThrownBy(() -> attemptService.submitMCQAttempt(1L, owner, 1, 30))
                .isInstanceOf(QuestionNotYetAvailableException.class);
    }

    @Test
    void giveUpAttempt_onRealInterview_outOfOrder_throwsQuestionNotYetAvailable() {
        SessionQuestion sq = sessionQuestionOwnedBy(owner, SessionStatus.IN_PROGRESS, QuestionType.CODING);
        sq.getSession().setSessionType(SessionType.REAL_INTERVIEW);
        SessionQuestion earlierUnansweredQuestion = SessionQuestion.builder().id(999L).orderIndex(1).build();
        when(sessionQuestionRepository.findById(1L)).thenReturn(Optional.of(sq));
        when(sessionQuestionRepository.findFirstBySession_IdAndAttemptIsNullOrderByOrderIndexAsc(1L))
                .thenReturn(Optional.of(earlierUnansweredQuestion));

        // Give-up (expectedType == null) is not exempt from the ordering guard.
        assertThatThrownBy(() -> attemptService.giveUpAttempt(1L, owner))
                .isInstanceOf(QuestionNotYetAvailableException.class);
    }

    @Test
    void getAttemptForUser_forAnotherUsersAttempt_throwsSessionAccessDenied() {
        Attempt attempt = Attempt.builder().id(5L).user(owner).build();
        when(attemptRepository.findById(5L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.getAttemptForUser(5L, intruder))
                .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    void getAttemptForUser_unknownId_throwsAttemptNotFound() {
        when(attemptRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attemptService.getAttemptForUser(999L, owner))
                .isInstanceOf(interview_coach.exceptions.AttemptNotFoundException.class);
    }
}