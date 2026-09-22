package interview_coach.controllertests;

import interview_coach.controllers.SessionController;
import interview_coach.entities.Attempt;
import interview_coach.entities.Question;
import interview_coach.entities.Session;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.User;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.repositories.VoiceAnswerRepository;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the completeSession summary: maxScore must reflect
 * every SessionQuestion in the session, not just the ones with an Attempt
 * row - previously it was accumulated by looping only answered questions,
 * so an unanswered question silently vanished from the denominator.
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

        mockMvc.perform(post("/api/sessions/10/complete").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(100)) // 10 questions x 10, not 3 x 10
                .andExpect(jsonPath("$.results.length()").value(3)); // only answered questions in the per-question breakdown
    }
}
