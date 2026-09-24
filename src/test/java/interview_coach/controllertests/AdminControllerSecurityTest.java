package interview_coach.controllertests;

import interview_coach.InterviewCoachApplication;
import interview_coach.services.core.OptionService;
import interview_coach.services.core.QuestionService;
import interview_coach.services.core.TestCaseService;
import interview_coach.services.core.TopicService;
import interview_coach.services.core.UserService;
import interview_coach.services.core.CodingChallengeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves /api/admin/** is actually restricted to ROLE_ADMIN by loading the real
 * SecurityConfig + JwtFilter (unlike AdminControllerTest's @WebMvcTest slice, which
 * excludes JwtFilter and never loads SecurityConfig at all, so it cannot verify this).
 *
 * JwtFilter only touches SecurityContextHolder when it finds a Bearer header and never
 * clears an existing context, so @WithMockUser's Authentication survives untouched into
 * the real authorization check. No Authorization header at all leaves the request
 * anonymous, and SecurityConfig registers no AuthenticationEntryPoint, so Spring
 * Security's default for "unauthenticated" here is 403 (Http403ForbiddenEntryPoint),
 * not 401 - asserted below rather than assumed.
 */
@SpringBootTest(classes = InterviewCoachApplication.class)
@AutoConfigureMockMvc
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

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

    // ---------- Users area ----------

    @Test
    @WithMockUser(roles = "USER")
    void users_asRegularUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void users_anonymous_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void users_asAdmin_returns200() throws Exception {
        when(userService.getUsers(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }

    // ---------- Questions area ----------

    @Test
    @WithMockUser(roles = "USER")
    void questions_asRegularUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/questions"))
                .andExpect(status().isForbidden());
    }

    @Test
    void questions_anonymous_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/questions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void questions_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/questions"))
                .andExpect(status().isOk());
    }

    // ---------- Topics area ----------

    @Test
    @WithMockUser(roles = "USER")
    void topics_asRegularUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/topics"))
                .andExpect(status().isForbidden());
    }

    @Test
    void topics_anonymous_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/topics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void topics_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/topics"))
                .andExpect(status().isOk());
    }
}
