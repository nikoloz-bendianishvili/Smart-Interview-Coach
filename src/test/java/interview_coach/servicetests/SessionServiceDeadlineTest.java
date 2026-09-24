package interview_coach.servicetests;

import interview_coach.dto.SessionDTO;
import interview_coach.entities.Question;
import interview_coach.entities.Session;
import interview_coach.entities.Topic;
import interview_coach.entities.User;
import interview_coach.enums.InteractionMode;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionType;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies SessionService's deadline math per SessionType, against a fixed Clock so the
 * expected deadline is an exact value rather than "roughly now plus something."
 * Multipliers: REAL_INTERVIEW 1.10, FREE_MOCK 1.50, CUSTOM_PRACTICE 2.00 (matching the
 * application.yaml defaults) - set explicitly here since @Value only applies under Spring.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceDeadlineTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

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
                sessionQuestionRepository, FIXED_CLOCK);
        ReflectionTestUtils.setField(sessionService, "realInterviewMultiplier", 1.10);
        ReflectionTestUtils.setField(sessionService, "freeMockMultiplier", 1.50);
        ReflectionTestUtils.setField(sessionService, "customPracticeMultiplier", 2.00);
        ReflectionTestUtils.setField(sessionService, "gracePeriodSeconds", 30L);

        user = User.builder().id(1L).build();
        topic = Topic.builder().id(5L).topicName("Java").build();

        when(sessionRepository.save(any())).thenAnswer(inv -> {
            Session session = inv.getArgument(0, Session.class);
            session.setId(100L);
            return session;
        });
    }

    private List<Question> questionsWithTimeLimit(QuestionType type, int... timeLimits) {
        return java.util.stream.IntStream.range(0, timeLimits.length)
                .mapToObj(i -> Question.builder().id((long) i + 1).questionType(type)
                        .timeLimit(timeLimits[i]).score(10).build())
                .toList();
    }

    @Test
    void realInterview_deadlineIs110PercentOfChosenMinutes() {
        when(questionRepository.findAverageTimeLimitByType(QuestionType.CODING)).thenReturn(300.0);
        when(questionRepository.findAverageTimeLimitByType(QuestionType.OPEN_ENDED)).thenReturn(180.0);
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.CODING)).thenReturn(10L);
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.OPEN_ENDED)).thenReturn(10L);
        when(questionRepository.findRandomByType(eq("CODING"), anyInt())).thenReturn(questionsWithTimeLimit(QuestionType.CODING, 300, 300));
        when(questionRepository.findRandomByType(eq("OPEN_ENDED"), anyInt())).thenReturn(questionsWithTimeLimit(QuestionType.OPEN_ENDED, 180, 180, 180, 180, 180, 180, 180, 180));

        SessionDTO dto = new SessionDTO(user, null, SessionType.REAL_INTERVIEW, null, InteractionMode.TEXT, null, 30);

        Session result = sessionService.startSession(dto);

        // 30 min * 60 * 1.10 = 1980s
        assertThat(result.getSessionDeadline()).isEqualTo(NOW.plusSeconds(1980));
    }

    @Test
    void freeMock_byChosenTime_deadlineIs150PercentOfThatTime() {
        SessionDTO dto = new SessionDTO(user, null, SessionType.FREE_MOCK, null, InteractionMode.TEXT, null, 20);

        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.CODING)).thenReturn(10L);
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.OPEN_ENDED)).thenReturn(10L);
        when(questionRepository.findAverageTimeLimitByType(QuestionType.CODING)).thenReturn(300.0);
        when(questionRepository.findAverageTimeLimitByType(QuestionType.OPEN_ENDED)).thenReturn(180.0);
        when(questionRepository.findRandomByType(eq("CODING"), anyInt())).thenReturn(questionsWithTimeLimit(QuestionType.CODING, 300));
        when(questionRepository.findRandomByType(eq("OPEN_ENDED"), anyInt())).thenReturn(questionsWithTimeLimit(QuestionType.OPEN_ENDED, 180, 180, 180));

        Session result = sessionService.startSession(dto);

        // 20 min * 60 * 1.50 = 1800s - driven by the CHOSEN time, not the selected questions
        assertThat(result.getSessionDeadline()).isEqualTo(NOW.plusSeconds(1800));
    }

    @Test
    void freeMock_byChosenCount_deadlineIs150PercentOfSummedQuestionTimeLimit() {
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.CODING)).thenReturn(10L);
        when(questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.OPEN_ENDED)).thenReturn(10L);
        // total=4 -> openEnded=round(4*0.75)=3, coding=1
        when(questionRepository.findRandomByType(eq("CODING"), anyInt())).thenReturn(questionsWithTimeLimit(QuestionType.CODING, 300));
        when(questionRepository.findRandomByType(eq("OPEN_ENDED"), anyInt())).thenReturn(questionsWithTimeLimit(QuestionType.OPEN_ENDED, 100, 100, 100));

        SessionDTO dto = new SessionDTO(user, null, SessionType.FREE_MOCK, null, InteractionMode.TEXT, 4, null);

        Session result = sessionService.startSession(dto);

        // summed timeLimit = 300 + 100*3 = 600s; 600 * 1.50 = 900s
        assertThat(result.getSessionDeadline()).isEqualTo(NOW.plusSeconds(900));
    }

    @Test
    void customPractice_deadlineIs200PercentOfSummedQuestionTimeLimit() {
        when(questionRepository.countByTopicIdAndQuestionTypeAndActiveTrue(5L, QuestionType.MCQ)).thenReturn(10L);
        when(questionRepository.findRandomByTopicAndType(5L, "MCQ", 3))
                .thenReturn(questionsWithTimeLimit(QuestionType.MCQ, 60, 90, 150));

        SessionDTO dto = new SessionDTO(user, topic, SessionType.CUSTOM_PRACTICE, QuestionType.MCQ, InteractionMode.TEXT, 3, null);

        Session result = sessionService.startSession(dto);

        // summed timeLimit = 60+90+150 = 300s; 300 * 2.00 = 600s
        assertThat(result.getSessionDeadline()).isEqualTo(NOW.plusSeconds(600));
    }
}
