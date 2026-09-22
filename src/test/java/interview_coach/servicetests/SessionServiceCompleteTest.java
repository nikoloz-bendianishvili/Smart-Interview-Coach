package interview_coach.servicetests;

import interview_coach.entities.*;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.exceptions.SessionAccessDeniedException;
import interview_coach.exceptions.SessionAlreadyCompletedException;
import interview_coach.exceptions.SessionNotReadyException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.SessionQuestionRepository;
import interview_coach.repositories.SessionRepository;
import interview_coach.services.core.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies SessionService.completeSession's ownership check, the IN_PROGRESS
 * guard, and the existing PENDING-attempts guard.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceCompleteTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private SessionQuestionRepository sessionQuestionRepository;

    private SessionService sessionService;

    private User owner;
    private User intruder;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, questionRepository, attemptRepository, sessionQuestionRepository);
        owner = User.builder().id(1L).build();
        intruder = User.builder().id(2L).build();
    }

    private Session sessionFor(User user, SessionStatus status) {
        return Session.builder().id(10L).user(user).status(status).sessionType(SessionType.FREE_MOCK).build();
    }

    @Test
    void completeSession_forAnotherUsersSession_throwsSessionAccessDenied() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(sessionFor(owner, SessionStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> sessionService.completeSession(10L, intruder))
                .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    void completeSession_alreadyCompleted_throwsSessionAlreadyCompleted() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(sessionFor(owner, SessionStatus.COMPLETED)));

        assertThatThrownBy(() -> sessionService.completeSession(10L, owner))
                .isInstanceOf(SessionAlreadyCompletedException.class);
    }

    @Test
    void completeSession_withPendingAttempts_throwsSessionNotReady() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(sessionFor(owner, SessionStatus.IN_PROGRESS)));
        when(attemptRepository.countBySessionQuestion_Session_IdAndStatus(10L, AttemptStatus.PENDING)).thenReturn(1L);

        assertThatThrownBy(() -> sessionService.completeSession(10L, owner))
                .isInstanceOf(SessionNotReadyException.class);
    }

    @Test
    void completeSession_happyPath_setsCompletedStatusEndTimeAndTotalScore() {
        Session session = sessionFor(owner, SessionStatus.IN_PROGRESS);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(attemptRepository.countBySessionQuestion_Session_IdAndStatus(10L, AttemptStatus.PENDING)).thenReturn(0L);

        Question question = Question.builder().id(1L).score(10).build();
        SessionQuestion sq = SessionQuestion.builder().id(1L).session(session).question(question).build();
        Attempt gradedAttempt = Attempt.builder().sessionQuestion(sq).status(AttemptStatus.GRADED).score(8).build();
        Attempt skippedAttempt = Attempt.builder().sessionQuestion(sq).status(AttemptStatus.SKIPPED).score(0).build();
        when(attemptRepository.findBySessionQuestion_Session_Id(10L)).thenReturn(List.of(gradedAttempt, skippedAttempt));

        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.completeSession(10L, owner);

        assertThat(result.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(result.getEndTime()).isNotNull();
        assertThat(result.getTotalScore()).isEqualTo(8); // skipped attempt excluded
    }
}