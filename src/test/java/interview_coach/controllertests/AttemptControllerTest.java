package interview_coach.controllertests;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview_coach.controllers.AttemptController;
import interview_coach.dto.CodeAttemptRequest;
import interview_coach.dto.McqAttemptRequest;
import interview_coach.entities.Attempt;
import interview_coach.entities.Question;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.User;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.QuestionType;
import interview_coach.exceptions.AttemptAlreadyExistsException;
import interview_coach.exceptions.SessionAccessDeniedException;
import interview_coach.exceptions.SessionQuestionNotFoundException;
import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import interview_coach.security.filter.JwtFilter;
import interview_coach.services.core.AttemptService;
import interview_coach.services.core.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies AttemptController's HTTP contract: status codes for the happy paths
 * (201 for the synchronously-graded MCQ, 202 + Location for the async code/answer
 * submits), 400 on Bean Validation failures, and the exception-to-status mapping
 * declared via @ResponseStatus on the domain exceptions.
 */
// JwtFilter is excluded because its own dependencies (JwtService, UserRepository) aren't
// part of this slice; Spring Security's default auto-configured filter chain still runs
// (unauthenticated-request tests are covered separately, not here) so that
// @WithMockUser's Authentication is attached to the request the way JwtFilter does in production.
@WebMvcTest(
        controllers = AttemptController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class)
)
class AttemptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Request DTOs here are plain records of primitives/strings, so a bare
    // ObjectMapper needs no extra modules; avoids depending on the JacksonAutoConfiguration
    // bean being present in this trimmed-down @WebMvcTest slice.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AttemptService attemptService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CodeSubmissionRepository codeSubmissionRepository;

    @MockitoBean
    private VoiceAnswerRepository voiceAnswerRepository;

    private static final String USER_EMAIL = "user@example.com";

    private Attempt mcqAttempt() {
        Question question = Question.builder().id(1L).questionType(QuestionType.MCQ).score(5).build();
        SessionQuestion sq = SessionQuestion.builder().id(2L).question(question).orderIndex(1).build();
        return Attempt.builder().id(100L).sessionQuestion(sq).status(AttemptStatus.GRADED)
                .score(5).isCorrect(true).timeTakenSeconds(30).build();
    }

    private Attempt pendingCodingAttempt() {
        Question question = Question.builder().id(1L).questionType(QuestionType.CODING).score(10).build();
        SessionQuestion sq = SessionQuestion.builder().id(2L).question(question).orderIndex(1).build();
        return Attempt.builder().id(101L).sessionQuestion(sq).status(AttemptStatus.PENDING).timeTakenSeconds(60).build();
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void submitMcq_returns201WithLocation() throws Exception {
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(User.builder().id(1L).email(USER_EMAIL).build());
        when(attemptService.submitMCQAttempt(anyLong(), any(User.class), any(Integer.class), any(Integer.class)))
                .thenReturn(mcqAttempt());

        McqAttemptRequest request = new McqAttemptRequest(2, 30);

        mockMvc.perform(post("/api/attempts/2/mcq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/attempts/100")))
                .andExpect(jsonPath("$.attemptId").value(100))
                .andExpect(jsonPath("$.status").value("GRADED"))
                .andExpect(jsonPath("$.isCorrect").value(true));
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void submitCode_returns202WithLocationAndPendingStatus() throws Exception {
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(User.builder().id(1L).email(USER_EMAIL).build());
        when(attemptService.submitCodingAttempt(anyLong(), any(User.class), any(String.class), any(Integer.class)))
                .thenReturn(pendingCodingAttempt());
        when(codeSubmissionRepository.findByAttemptId(101L)).thenReturn(Optional.empty());

        CodeAttemptRequest request = new CodeAttemptRequest("public class Solution {}", 60);

        mockMvc.perform(post("/api/attempts/2/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/attempts/101")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.score").doesNotExist());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void submitMcq_withOutOfRangeSelectedOption_returns400() throws Exception {
        McqAttemptRequest request = new McqAttemptRequest(9, 30);

        mockMvc.perform(post("/api/attempts/2/mcq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void submitCode_withBlankSourceCode_returns400() throws Exception {
        CodeAttemptRequest request = new CodeAttemptRequest("   ", 60);

        mockMvc.perform(post("/api/attempts/2/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void submitMcq_unknownSessionQuestion_returns404() throws Exception {
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(User.builder().id(1L).email(USER_EMAIL).build());
        when(attemptService.submitMCQAttempt(anyLong(), any(User.class), any(Integer.class), any(Integer.class)))
                .thenThrow(new SessionQuestionNotFoundException("SessionQuestion not found"));

        McqAttemptRequest request = new McqAttemptRequest(1, 30);

        mockMvc.perform(post("/api/attempts/999/mcq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void submitMcq_forAnotherUsersSession_returns403() throws Exception {
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(User.builder().id(1L).email(USER_EMAIL).build());
        when(attemptService.submitMCQAttempt(anyLong(), any(User.class), any(Integer.class), any(Integer.class)))
                .thenThrow(new SessionAccessDeniedException("This session does not belong to the current user."));

        McqAttemptRequest request = new McqAttemptRequest(1, 30);

        mockMvc.perform(post("/api/attempts/2/mcq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USER_EMAIL)
    void submitMcq_alreadyAnswered_returns409() throws Exception {
        when(userService.getUserByEmail(USER_EMAIL)).thenReturn(User.builder().id(1L).email(USER_EMAIL).build());
        when(attemptService.submitMCQAttempt(anyLong(), any(User.class), any(Integer.class), any(Integer.class)))
                .thenThrow(new AttemptAlreadyExistsException("This question has already been answered."));

        McqAttemptRequest request = new McqAttemptRequest(1, 30);

        mockMvc.perform(post("/api/attempts/2/mcq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }
}