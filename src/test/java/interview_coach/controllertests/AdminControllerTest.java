package interview_coach.controllertests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import interview_coach.controllers.AdminController;
import interview_coach.dto.*;
import interview_coach.entities.*;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import interview_coach.enums.Role;
import interview_coach.exceptions.TopicAlreadyExistsException;
import interview_coach.exceptions.TopicNotFoundException;
import interview_coach.exceptions.UserNotFoundException;
import interview_coach.security.filter.JwtFilter;
import interview_coach.services.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies AdminController's HTTP contract (status codes, Location headers, response
 * shapes) with the security filter chain excluded — the "is this actually admin-only"
 * question is answered separately by AdminControllerSecurityTest, which loads the real
 * SecurityConfig instead of stubbing role checks away.
 */
@WebMvcTest(
        controllers = AdminController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class)
)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private QuestionService questionService;
    @MockitoBean
    private TopicService topicService;
    @MockitoBean
    private OptionService optionService;
    @MockitoBean
    private CodingChallengeService codingChallengeService;
    @MockitoBean
    private TestCaseService testCaseService;

    private static final String ADMIN_EMAIL = "admin@example.com";

    private User admin(Long id) {
        return User.builder().id(id).email(ADMIN_EMAIL).role(Role.ADMIN).build();
    }

    // ---------- Users ----------

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void getUsers_returns200WithPage() throws Exception {
        User u = User.builder().id(5L).firstName("Bob").lastName("Jones").webName("bobby")
                .email("bob@example.com").role(Role.USER).isBanned(false).isVerified(true)
                .createdAt(LocalDateTime.now()).build();
        when(userService.getUsers(isNull(), any())).thenReturn(new PageImpl<>(List.of(u), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.content[0].webName").value("bobby"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void getUser_unknownId_returns404() throws Exception {
        when(userService.getUserById(999L)).thenThrow(new UserNotFoundException("User not found with id: 999"));

        mockMvc.perform(get("/api/admin/users/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void banUser_happyPath_returns204() throws Exception {
        when(userService.getUserByEmail(ADMIN_EMAIL)).thenReturn(admin(1L));
        BanRequest request = new BanRequest(LocalDateTime.now().plusDays(7));

        mockMvc.perform(put("/api/admin/users/5/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(userService).banUser(eq(5L), any(LocalDateTime.class));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void banUser_self_returns403() throws Exception {
        when(userService.getUserByEmail(ADMIN_EMAIL)).thenReturn(admin(1L));
        BanRequest request = new BanRequest(null);

        mockMvc.perform(put("/api/admin/users/1/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(userService, never()).banUser(anyLong(), any());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void unbanUser_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/users/5/ban").with(csrf()))
                .andExpect(status().isNoContent());

        verify(userService).unbanUser(5L);
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void changeRole_self_returns403() throws Exception {
        when(userService.getUserByEmail(ADMIN_EMAIL)).thenReturn(admin(1L));
        RoleUpdateRequest request = new RoleUpdateRequest(Role.USER);

        mockMvc.perform(put("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(userService, never()).changeRole(anyLong(), any());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void deleteUser_happyPath_returns204() throws Exception {
        when(userService.getUserByEmail(ADMIN_EMAIL)).thenReturn(admin(1L));

        mockMvc.perform(delete("/api/admin/users/5").with(csrf()))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(5L);
    }

    // ---------- Topics ----------

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void createTopic_returns201WithLocation() throws Exception {
        doAnswer(inv -> {
            Topic t = inv.getArgument(0);
            t.setId(10L);
            return null;
        }).when(topicService).createTopic(any(Topic.class));

        TopicCreateRequest request = new TopicCreateRequest("Algorithms", "algo questions");

        mockMvc.perform(post("/api/admin/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/topics/10")))
                .andExpect(jsonPath("$.topicName").value("Algorithms"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void createTopic_duplicate_returns409() throws Exception {
        doThrow(new TopicAlreadyExistsException("Topic already exists: Algorithms"))
                .when(topicService).createTopic(any(Topic.class));

        TopicCreateRequest request = new TopicCreateRequest("Algorithms", "algo questions");

        mockMvc.perform(post("/api/admin/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void deleteTopic_unknown_returns404() throws Exception {
        doThrow(new TopicNotFoundException("Topic not found with id: 999"))
                .when(topicService).deleteTopic(999L);

        mockMvc.perform(delete("/api/admin/topics/999").with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------- Questions ----------

    private Topic topic() {
        return Topic.builder().id(3L).topicName("Algorithms").description("algo").build();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void createMcqQuestion_returns201AndIncludesAnswerKey() throws Exception {
        when(topicService.getTopicById(3L)).thenReturn(topic());
        doAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setId(50L);
            return null;
        }).when(questionService).createMCQQuestion(any(Question.class), any(Option.class));

        OptionCreateRequest option = new OptionCreateRequest(2, "opt1", "opt2", null, null);
        QuestionCreateRequest request = new QuestionCreateRequest(
                3L, "What is Big-O of binary search?", QuestionType.MCQ, Difficulty.EASY, 60, 5, "It halves the search space.",
                option, null);

        mockMvc.perform(post("/api/admin/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/questions/50")))
                .andExpect(jsonPath("$.correctOption").value(2))
                .andExpect(jsonPath("$.options[0]").value("opt1"))
                .andExpect(jsonPath("$.explanation").value("It halves the search space."));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void createMcqQuestion_missingOptionPayload_returns400() throws Exception {
        when(topicService.getTopicById(3L)).thenReturn(topic());

        QuestionCreateRequest request = new QuestionCreateRequest(
                3L, "What is Big-O of binary search?", QuestionType.MCQ, Difficulty.EASY, 60, 5, null,
                null, null);

        mockMvc.perform(post("/api/admin/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(questionService, never()).createMCQQuestion(any(), any());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void getQuestion_unknown_returns404() throws Exception {
        when(questionService.getQuestionById(999L))
                .thenThrow(new interview_coach.exceptions.QuestionNotFoundException("Question not found with id: 999"));

        mockMvc.perform(get("/api/admin/questions/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
    void deleteQuestion_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/questions/50").with(csrf()))
                .andExpect(status().isNoContent());

        verify(questionService).deleteQuestion(50L);
    }
}
