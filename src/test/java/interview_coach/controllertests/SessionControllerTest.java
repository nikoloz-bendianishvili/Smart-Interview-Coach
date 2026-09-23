package interview_coach.controllertests;

import interview_coach.controllers.SessionController;
import interview_coach.entities.Attempt;
import interview_coach.entities.CodeSubmission;
import interview_coach.entities.Option;
import interview_coach.entities.Question;
import interview_coach.entities.Session;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.User;
import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.dto.StartSessionRequest;
import interview_coach.enums.InteractionMode;
import interview_coach.exceptions.SessionAccessDeniedException;
import interview_coach.exceptions.SessionQuestionNotFoundException;
import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview_coach.security.filter.JwtFilter;
import interview_coach.services.core.AttemptService;
import interview_coach.services.core.OptionService;
import interview_coach.services.core.SessionService;
import interview_coach.services.core.TestCaseService;
import interview_coach.services.core.TopicService;
import interview_coach.services.core.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers completeSession's summary-building (including the maxScore-must-count-every-question
 * regression) and the GET status endpoint added alongside AWAITING_GRADING, including that both
 * routes share the exact same 202-while-grading/200-once-scored split.
 */
@WebMvcTest(
        controllers = SessionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class)
)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private AttemptService attemptService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private TopicService topicService;
    @MockitoBean
    private OptionService optionService;
    @MockitoBean
    private TestCaseService testCaseService;
    @MockitoBean
    private VoiceAnswerRepository voiceAnswerRepository;
    @MockitoBean
    private CodeSubmissionRepository codeSubmissionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String USER_EMAIL = "user@example.com";

    @Test
    @WithMockUser(username = USER_EMAIL)
    void completeSession_withUnansweredQuestions_maxScoreCountsAllQuestionsNotJustAnsweredOnes() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Session session = Session.builder()
                .id(10L).user(user).sessionType(SessionType.FREE_MOCK)
                .status(SessionStatus.COMPLETED).totalScore(8)
                .build();
        when(sessionService.completeSession(10L, user)).thenReturn(session);

        // 10 questions total, each worth 10 points -> maxScore should be 100.
        List<SessionQuestion> allQuestions = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Question q = Question.builder().id((long) i).questionType(QuestionType.MCQ).score(10)
                    .statement("Q" + i).build();
            allQuestions.add(SessionQuestion.builder().id((long) i).question(q).orderIndex(i).build());
        }
        when(sessionService.getSessionQuestions(10L)).thenReturn(allQuestions);

        // Only 3 of the 10 were actually answered.
        List<Attempt> answeredOnly = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            answeredOnly.add(Attempt.builder()
                    .sessionQuestion(allQuestions.get(i - 1))
                    .status(AttemptStatus.GRADED)
                    .score(8)
                    .build());
        }
        when(attemptService.getAttemptsBySession(10L)).thenReturn(answeredOnly);

        // Session is COMPLETED, so buildSummaryResponse looks up the correct option for each
        // answered MCQ question - every real MCQ question has one, so the mock should too.
        when(optionService.getOptionByQuestionId(anyLong()))
                .thenReturn(Option.builder().correctOption(1).option1("a").option2("b").build());

        mockMvc.perform(post("/api/sessions/10/complete").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(100)) // 10 questions x 10, not 3 x 10
                .andExpect(jsonPath("$.results.length()").value(3)); // only answered questions in the per-question breakdown
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void completeSession_awaitingGrading_returns202() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Session session = Session.builder().id(11L).user(user).sessionType(SessionType.FREE_MOCK)
                .status(SessionStatus.AWAITING_GRADING).build();
        when(sessionService.completeSession(11L, user)).thenReturn(session);
        when(sessionService.getSessionQuestions(11L)).thenReturn(List.of());
        when(attemptService.getAttemptsBySession(11L)).thenReturn(List.of());

        mockMvc.perform(post("/api/sessions/11/complete").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AWAITING_GRADING"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void getSession_completed_returns200() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Session session = Session.builder().id(12L).user(user).sessionType(SessionType.FREE_MOCK)
                .status(SessionStatus.COMPLETED).totalScore(5).build();
        when(sessionService.getSessionForUser(12L, user)).thenReturn(session);
        when(sessionService.getSessionQuestions(12L)).thenReturn(List.of());
        when(attemptService.getAttemptsBySession(12L)).thenReturn(List.of());

        mockMvc.perform(get("/api/sessions/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalScore").value(5));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void getSession_stillAwaitingGrading_returns202() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Session session = Session.builder().id(13L).user(user).sessionType(SessionType.FREE_MOCK)
                .status(SessionStatus.AWAITING_GRADING).build();
        when(sessionService.getSessionForUser(13L, user)).thenReturn(session);
        when(sessionService.getSessionQuestions(13L)).thenReturn(List.of());
        when(attemptService.getAttemptsBySession(13L)).thenReturn(List.of());

        mockMvc.perform(get("/api/sessions/13"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AWAITING_GRADING"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void getSession_forAnotherUsersSession_returns403() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);
        when(sessionService.getSessionForUser(14L, user))
                .thenThrow(new SessionAccessDeniedException("This session does not belong to the current user."));

        mockMvc.perform(get("/api/sessions/14"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void startSession_realInterview_responseIncludesOnlyFirstQuestion() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Session session = Session.builder().id(20L).user(user).sessionType(SessionType.REAL_INTERVIEW).build();
        when(sessionService.startSession(any())).thenReturn(session);

        List<SessionQuestion> allThree = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Question q = Question.builder().id((long) i).questionType(QuestionType.OPEN_ENDED)
                    .statement("Q" + i).timeLimit(120).score(15).build();
            allThree.add(SessionQuestion.builder().id((long) i).question(q).orderIndex(i).build());
        }
        when(sessionService.getSessionQuestions(20L)).thenReturn(allThree);

        StartSessionRequest request = new StartSessionRequest(
                SessionType.REAL_INTERVIEW, null, null, InteractionMode.TEXT, null, 30);

        mockMvc.perform(post("/api/sessions/start")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(3)) // full count still reported
                .andExpect(jsonPath("$.questions.length()").value(1)) // but only question 1 is revealed
                .andExpect(jsonPath("$.questions[0].orderIndex").value(1));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void startSession_freeMock_responseIncludesEveryQuestion() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Session session = Session.builder().id(21L).user(user).sessionType(SessionType.FREE_MOCK).build();
        when(sessionService.startSession(any())).thenReturn(session);

        List<SessionQuestion> allThree = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Question q = Question.builder().id((long) i).questionType(QuestionType.OPEN_ENDED)
                    .statement("Q" + i).timeLimit(120).score(15).build();
            allThree.add(SessionQuestion.builder().id((long) i).question(q).orderIndex(i).build());
        }
        when(sessionService.getSessionQuestions(21L)).thenReturn(allThree);

        StartSessionRequest request = new StartSessionRequest(
                SessionType.FREE_MOCK, null, null, InteractionMode.TEXT, 3, null);

        mockMvc.perform(post("/api/sessions/start")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(3))
                .andExpect(jsonPath("$.questions.length()").value(3)); // unordered types get everything up front
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void getCurrentQuestion_returnsTheCurrentQuestion() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Question question = Question.builder().id(2L).questionType(QuestionType.OPEN_ENDED)
                .statement("Q2").timeLimit(120).score(15).build();
        SessionQuestion sq = SessionQuestion.builder().id(2L).question(question).orderIndex(2).build();
        when(sessionService.getCurrentQuestion(20L, user)).thenReturn(sq);

        mockMvc.perform(get("/api/sessions/20/current-question"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderIndex").value(2))
                .andExpect(jsonPath("$.statement").value("Q2"));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void getCurrentQuestion_nothingLeft_returns404() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);
        when(sessionService.getCurrentQuestion(20L, user))
                .thenThrow(new SessionQuestionNotFoundException("No current question"));

        mockMvc.perform(get("/api/sessions/20/current-question"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void getMySessions_returnsCardsNewestFirstWithMaxScore() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Session recent = Session.builder().id(41L).user(user).sessionType(SessionType.FREE_MOCK)
                .status(SessionStatus.COMPLETED).totalScore(18).build();
        Session older = Session.builder().id(40L).user(user).sessionType(SessionType.CUSTOM_PRACTICE)
                .status(SessionStatus.IN_PROGRESS).build();
        // getSessionsByUserDesc is responsible for the newest-first ordering itself; the
        // controller just maps whatever order it returns.
        when(sessionService.getSessionsByUserDesc(1L)).thenReturn(List.of(recent, older));

        Question q1 = Question.builder().id(1L).questionType(QuestionType.MCQ).score(10).build();
        Question q2 = Question.builder().id(2L).questionType(QuestionType.MCQ).score(10).build();
        when(sessionService.getSessionQuestions(41L)).thenReturn(List.of(
                SessionQuestion.builder().id(1L).question(q1).orderIndex(1).build(),
                SessionQuestion.builder().id(2L).question(q2).orderIndex(2).build()
        ));
        when(sessionService.getSessionQuestions(40L)).thenReturn(List.of());

        mockMvc.perform(get("/api/sessions/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sessionId").value(41))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].maxScore").value(20)) // 2 questions x 10
                .andExpect(jsonPath("$[1].sessionId").value(40))
                .andExpect(jsonPath("$[1].maxScore").value(0));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void getSession_inProgress_hidesCorrectOption() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Session session = Session.builder().id(50L).user(user).sessionType(SessionType.CUSTOM_PRACTICE)
                .status(SessionStatus.IN_PROGRESS).build();
        when(sessionService.getSessionForUser(50L, user)).thenReturn(session);

        Question question = Question.builder().id(5L).questionType(QuestionType.MCQ).score(10).build();
        SessionQuestion sq = SessionQuestion.builder().id(5L).question(question).orderIndex(1).build();
        when(sessionService.getSessionQuestions(50L)).thenReturn(List.of(sq));

        Attempt attempt = Attempt.builder().id(100L).sessionQuestion(sq)
                .status(AttemptStatus.GRADED).score(10).selectedOption(2).build();
        when(attemptService.getAttemptsBySession(50L)).thenReturn(List.of(attempt));

        mockMvc.perform(get("/api/sessions/50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].selectedOption").value(2))
                .andExpect(jsonPath("$.results[0].correctOption").value(nullValue()));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void getSession_completed_showsCorrectOptionAndOwnAnswers() throws Exception {
        User user = User.builder().id(1L).email(USER_EMAIL).build();
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(user);

        Session session = Session.builder().id(51L).user(user).sessionType(SessionType.FREE_MOCK)
                .status(SessionStatus.COMPLETED).build();
        when(sessionService.getSessionForUser(51L, user)).thenReturn(session);

        Question mcq = Question.builder().id(6L).questionType(QuestionType.MCQ).score(10).build();
        Question openEnded = Question.builder().id(7L).questionType(QuestionType.OPEN_ENDED).score(15).build();
        Question coding = Question.builder().id(8L).questionType(QuestionType.CODING).score(20).build();

        SessionQuestion mcqSq = SessionQuestion.builder().id(6L).question(mcq).orderIndex(1).build();
        SessionQuestion openEndedSq = SessionQuestion.builder().id(7L).question(openEnded).orderIndex(2).build();
        SessionQuestion codingSq = SessionQuestion.builder().id(8L).question(coding).orderIndex(3).build();
        when(sessionService.getSessionQuestions(51L)).thenReturn(List.of(mcqSq, openEndedSq, codingSq));

        Attempt mcqAttempt = Attempt.builder().id(200L).sessionQuestion(mcqSq)
                .status(AttemptStatus.GRADED).score(10).selectedOption(3).build();
        Attempt openEndedAttempt = Attempt.builder().id(201L).sessionQuestion(openEndedSq)
                .status(AttemptStatus.GRADED).score(12).textAnswer("My answer").build();
        Attempt codingAttempt = Attempt.builder().id(202L).sessionQuestion(codingSq)
                .status(AttemptStatus.GRADED).score(20).build();
        when(attemptService.getAttemptsBySession(51L)).thenReturn(List.of(mcqAttempt, openEndedAttempt, codingAttempt));

        when(optionService.getOptionByQuestionId(6L))
                .thenReturn(Option.builder().correctOption(3).option1("a").option2("b").build());
        when(voiceAnswerRepository.findByAttemptId(201L))
                .thenReturn(Optional.of(VoiceAnswer.builder().aiFeedback("Good answer").build()));
        when(codeSubmissionRepository.findByAttemptId(202L))
                .thenReturn(Optional.of(CodeSubmission.builder()
                        .sourceCode("print('hi')").passedTestCount(2).totalTestCount(3).build()));

        mockMvc.perform(get("/api/sessions/51"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].selectedOption").value(3))
                .andExpect(jsonPath("$.results[0].correctOption").value(3))
                .andExpect(jsonPath("$.results[1].textAnswer").value("My answer"))
                .andExpect(jsonPath("$.results[1].aiFeedback").value("Good answer"))
                .andExpect(jsonPath("$.results[2].sourceCode").value("print('hi')"))
                .andExpect(jsonPath("$.results[2].passedTestCount").value(2))
                .andExpect(jsonPath("$.results[2].totalTestCount").value(3));
    }
}
