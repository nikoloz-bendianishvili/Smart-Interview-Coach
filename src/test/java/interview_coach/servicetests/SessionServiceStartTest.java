package interview_coach.servicetests;

import interview_coach.dto.SessionDTO;
import interview_coach.entities.Question;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.Topic;
import interview_coach.entities.User;
import interview_coach.enums.InteractionMode;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.exceptions.InsufficientQuestionsException;
import interview_coach.exceptions.InvalidSessionRequestException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.SessionQuestionRepository;
import interview_coach.repositories.SessionRepository;
import interview_coach.services.core.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies SessionService.startSession's question-selection algorithm for
 * all three SessionTypes: CUSTOM_PRACTICE's availability check,
 * REAL_INTERVIEW's time-derived question count, and FREE_MOCK's
 * exactly-one-of validation - plus the OPEN_ENDED_RATIO (0.75) split shared
 * by REAL_INTERVIEW and FREE_MOCK, and orderIndex assignment.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceStartTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private SessionQuestionRepository sessionQuestionRepository;

    private SessionService sessionService;

    private User user;
    private Topic topic;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, questionRepository, attemptRepository,
                sessionQuestionRepository, Clock.systemDefaultZone());
        // @Value fields are only populated by Spring; set them explicitly for this plain
        // Mockito unit test so computeDeadline (called at the end of every startSession) has
        // real multiplier values rather than Java's 0.0 default.
        ReflectionTestUtils.setField(sessionService, "realInterviewMultiplier", 1.10);
        ReflectionTestUtils.setField(sessionService, "freeMockMultiplier", 1.50);
        ReflectionTestUtils.setField(sessionService, "customPracticeMultiplier", 2.00);
        ReflectionTestUtils.setField(sessionService, "gracePeriodSeconds", 30L);
        user = User.builder().id(1L).build();
        topic = Topic.builder().id(5L).topicName("Java").build();

        // Session.save just returns whatever entity it was given, with an id assigned.
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            var session = inv.getArgument(0, interview_coach.entities.Session.class);
            session.setId(100L);
            return session;
        });
    }

    private List<Question> questionsOfType(QuestionType type, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> Question.builder().id((long) i).questionType(type).timeLimit(60).score(10).build())
                .toList();
    }

    // ---------- CUSTOM_PRACTICE ----------

    @Test
    void startSession_customPractice_insufficientQuestions_throwsInsufficientQuestions() {
        when(questionRepository.countByTopicIdAndQuestionTypeAndActiveTrue(5L, QuestionType.MCQ)).thenReturn(3L);

        SessionDTO dto = new SessionDTO(user, topic, SessionType.CUSTOM_PRACTICE, QuestionType.MCQ,
                InteractionMode.TEXT, 5, null);

        assertThatThrownBy(() -> sessionService.startSession(dto))
                .isInstanceOf(InsufficientQuestionsException.class);
    }

    @Test
    void startSession_customPractice_happyPath_selectsRequestedCountAndAssignsSequentialOrderIndex() {
        when(questionRepository.countByTopicIdAndQuestionTypeAndActiveTrue(5L, QuestionType.MCQ)).thenReturn(10L);
        when(questionRepository.findRandomByTopicAndType(5L, "MCQ", 5)).thenReturn(questionsOfType(QuestionType.MCQ, 5));

        SessionDTO dto = new SessionDTO(user, topic, SessionType.CUSTOM_PRACTICE, QuestionType.MCQ,
                InteractionMode.TEXT, 5, null);

        sessionService.startSession(dto);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionQuestion>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(sessionQuestionRepository).saveAll(captor.capture());

        List<SessionQuestion> saved = captor.getValue();
        assertThat(saved).hasSize(5);
        assertThat(saved.stream().map(SessionQuestion::getOrderIndex).toList())
                .containsExactly(1, 2, 3, 4, 5);
    }

    // ---------- FREE_MOCK ----------

    @Test
    void startSession_freeMock_bothCountAndTimeLimitProvided_throwsInvalidSessionRequest() {
        SessionDTO dto = new SessionDTO(user, null, SessionType.FREE_MOCK, null,
                InteractionMode.TEXT, 10, 30);

        assertThatThrownBy(() -> sessionService.startSession(dto))
                .isInstanceOf(InvalidSessionRequestException.class);
    }

    @Test
    void startSession_freeMock_neitherCountNorTimeLimitProvided_throwsInvalidSessionRequest() {
        SessionDTO dto = new SessionDTO(user, null, SessionType.FREE_MOCK, null,
                InteractionMode.TEXT, null, null);

        assertThatThrownBy(() -> sessionService.startSession(dto))
                .isInstanceOf(InvalidSessionRequestException.class);
    }

    @Test
    void startSession_freeMock_withNumOfQuestions_splitsAtOpenEndedRatio() {
        // total = 8 -> openEnded = round(8 * 0.75) = 6, coding = 2
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.CODING)).thenReturn(10L);
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.OPEN_ENDED)).thenReturn(10L);
        when(questionRepository.findRandomByType(eq("CODING"), anyInt())).thenReturn(questionsOfType(QuestionType.CODING, 2));
        when(questionRepository.findRandomByType(eq("OPEN_ENDED"), anyInt())).thenReturn(questionsOfType(QuestionType.OPEN_ENDED, 6));

        SessionDTO dto = new SessionDTO(user, null, SessionType.FREE_MOCK, null,
                InteractionMode.TEXT, 8, null);

        sessionService.startSession(dto);

        org.mockito.Mockito.verify(questionRepository).findRandomByType("CODING", 2);
        org.mockito.Mockito.verify(questionRepository).findRandomByType("OPEN_ENDED", 6);
    }

    @Test
    void startSession_freeMock_insufficientCodingOrOpenEnded_throwsInsufficientQuestions() {
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.CODING)).thenReturn(0L);
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.OPEN_ENDED)).thenReturn(10L);

        SessionDTO dto = new SessionDTO(user, null, SessionType.FREE_MOCK, null,
                InteractionMode.TEXT, 8, null);

        assertThatThrownBy(() -> sessionService.startSession(dto))
                .isInstanceOf(InsufficientQuestionsException.class);
    }

    // ---------- REAL_INTERVIEW ----------

    @Test
    void startSession_realInterview_derivesQuestionCountFromTimeLimit() {
        // avgOpenEnded = 180s, avgCoding = 300s -> avgPerQuestion = 0.75*180 + 0.25*300 = 210s
        // timeLimitMinutes = 35 -> 2100s / 210s = 10 questions -> openEnded 8, coding 2
        when(questionRepository.findAverageTimeLimitByType(QuestionType.CODING)).thenReturn(300.0);
        when(questionRepository.findAverageTimeLimitByType(QuestionType.OPEN_ENDED)).thenReturn(180.0);
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.CODING)).thenReturn(10L);
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.OPEN_ENDED)).thenReturn(10L);
        when(questionRepository.findRandomByType(eq("CODING"), anyInt())).thenReturn(questionsOfType(QuestionType.CODING, 2));
        when(questionRepository.findRandomByType(eq("OPEN_ENDED"), anyInt())).thenReturn(questionsOfType(QuestionType.OPEN_ENDED, 8));

        SessionDTO dto = new SessionDTO(user, null, SessionType.REAL_INTERVIEW, null,
                InteractionMode.TEXT, null, 35);

        sessionService.startSession(dto);

        org.mockito.Mockito.verify(questionRepository).findRandomByType("CODING", 2);
        org.mockito.Mockito.verify(questionRepository).findRandomByType("OPEN_ENDED", 8);
    }

    @Test
    void startSession_realInterview_missingTimeLimitMinutes_throwsInvalidSessionRequest() {
        SessionDTO dto = new SessionDTO(user, null, SessionType.REAL_INTERVIEW, null,
                InteractionMode.TEXT, null, null);

        assertThatThrownBy(() -> sessionService.startSession(dto))
                .isInstanceOf(InvalidSessionRequestException.class);
    }

    @Test
    void startSession_realInterview_noQuestionsToEstimateTimeFrom_throwsInsufficientQuestions() {
        when(questionRepository.findAverageTimeLimitByType(QuestionType.CODING)).thenReturn(null);
        when(questionRepository.findAverageTimeLimitByType(QuestionType.OPEN_ENDED)).thenReturn(180.0);

        SessionDTO dto = new SessionDTO(user, null, SessionType.REAL_INTERVIEW, null,
                InteractionMode.TEXT, null, 30);

        assertThatThrownBy(() -> sessionService.startSession(dto))
                .isInstanceOf(InsufficientQuestionsException.class);
    }

    @Test
    void startSession_setsStatusInProgress() {
        when(questionRepository.countByTopicIdAndQuestionTypeAndActiveTrue(5L, QuestionType.MCQ)).thenReturn(3L);
        when(questionRepository.findRandomByTopicAndType(5L, "MCQ", 3)).thenReturn(questionsOfType(QuestionType.MCQ, 3));

        SessionDTO dto = new SessionDTO(user, topic, SessionType.CUSTOM_PRACTICE, QuestionType.MCQ,
                InteractionMode.TEXT, 3, null);

        var session = sessionService.startSession(dto);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }
}
