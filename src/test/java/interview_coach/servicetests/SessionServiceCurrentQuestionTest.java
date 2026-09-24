package interview_coach.servicetests;

import interview_coach.entities.Session;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.User;
import interview_coach.exceptions.SessionAccessDeniedException;
import interview_coach.exceptions.SessionQuestionNotFoundException;
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

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Verifies SessionService.getCurrentQuestion: ownership enforcement, and that it correctly
 * surfaces "nothing left to answer" as a 404-mapped exception rather than null/empty leaking
 * out to the controller.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceCurrentQuestionTest {

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
        sessionService = new SessionService(sessionRepository, questionRepository, attemptRepository,
                sessionQuestionRepository, Clock.systemDefaultZone());
        owner = User.builder().id(1L).build();
        intruder = User.builder().id(2L).build();
    }

    @Test
    void getCurrentQuestion_returnsLowestOrderIndexUnansweredQuestion() {
        Session session = Session.builder().id(10L).user(owner).build();
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        SessionQuestion current = SessionQuestion.builder().id(3L).session(session).orderIndex(2).build();
        when(sessionQuestionRepository.findFirstBySession_IdAndAttemptIsNullOrderByOrderIndexAsc(10L))
                .thenReturn(Optional.of(current));

        SessionQuestion result = sessionService.getCurrentQuestion(10L, owner);

        assertThat(result.getId()).isEqualTo(3L);
    }

    @Test
    void getCurrentQuestion_forAnotherUsersSession_throwsSessionAccessDenied() {
        Session session = Session.builder().id(10L).user(owner).build();
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.getCurrentQuestion(10L, intruder))
                .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    void getCurrentQuestion_everyQuestionAlreadyAttempted_throwsSessionQuestionNotFound() {
        Session session = Session.builder().id(10L).user(owner).build();
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionQuestionRepository.findFirstBySession_IdAndAttemptIsNullOrderByOrderIndexAsc(10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getCurrentQuestion(10L, owner))
                .isInstanceOf(SessionQuestionNotFoundException.class);
    }
}
