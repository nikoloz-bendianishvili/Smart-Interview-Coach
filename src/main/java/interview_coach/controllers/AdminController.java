package interview_coach.controllers;

import interview_coach.dto.*;
import interview_coach.entities.*;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import interview_coach.exceptions.AdminActionNotAllowedException;
import interview_coach.exceptions.QuestionTypeMismatchException;
import interview_coach.services.core.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * All routes here sit under /api/admin/**, which SecurityConfig restricts to
 * hasRole("ADMIN") — that URL rule (not @PreAuthorize, which would be a
 * silent no-op since @EnableMethodSecurity is not configured) is what
 * produces the 403 for non-admin callers.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final QuestionService questionService;
    private final TopicService topicService;
    private final OptionService optionService;
    private final CodingChallengeService codingChallengeService;
    private final TestCaseService testCaseService;

    // ---------- Users ----------

    @GetMapping("/users")
    public ResponseEntity<Page<UserProfileResponse>> getUsers(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<User> users = userService.getUsers(query, pageable);
        return ResponseEntity.ok(users.map(this::toUserProfileResponse));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserProfileResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(toUserProfileResponse(userService.getUserById(userId)));
    }

    @PutMapping("/users/{userId}/ban")
    public ResponseEntity<Void> banUser(
            Authentication authentication,
            @PathVariable Long userId,
            @RequestBody BanRequest request) {

        ensureNotSelf(authentication, userId, "ban");
        userService.banUser(userId, request.expiresAt());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/ban")
    public ResponseEntity<Void> unbanUser(@PathVariable Long userId) {
        userService.unbanUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<Void> changeRole(
            Authentication authentication,
            @PathVariable Long userId,
            @Valid @RequestBody RoleUpdateRequest request) {

        ensureNotSelf(authentication, userId, "change the role of");
        userService.changeRole(userId, request.role());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(Authentication authentication, @PathVariable Long userId) {
        ensureNotSelf(authentication, userId, "delete");
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // ---------- Topics ----------

    @GetMapping("/topics")
    public ResponseEntity<List<TopicResponse>> getTopics() {
        return ResponseEntity.ok(topicService.getAllTopics().stream().map(this::toTopicResponse).toList());
    }

    @PostMapping("/topics")
    public ResponseEntity<TopicResponse> createTopic(
            @Valid @RequestBody TopicCreateRequest request,
            UriComponentsBuilder uriBuilder) {

        Topic topic = Topic.builder()
                .topicName(request.topicName())
                .description(request.description())
                .build();
        topicService.createTopic(topic);

        return ResponseEntity
                .created(uriBuilder.path("/api/topics/{id}").buildAndExpand(topic.getId()).toUri())
                .body(toTopicResponse(topic));
    }

    @PutMapping("/topics/{topicId}")
    public ResponseEntity<Void> updateTopic(@PathVariable Long topicId, @RequestBody TopicUpdateDTO request) {
        topicService.updateTopic(topicId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/topics/{topicId}")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long topicId) {
        topicService.deleteTopic(topicId);
        return ResponseEntity.noContent().build();
    }

    // ---------- Questions ----------

    @GetMapping("/questions")
    public ResponseEntity<List<AdminQuestionResponse>> getQuestions(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(defaultValue = "true") boolean includeInactive) {

        List<Question> questions = questionService.getAllQuestions();

        if (topicId != null) {
            questions = questions.stream()
                    .filter(q -> q.getTopic() != null && topicId.equals(q.getTopic().getId()))
                    .toList();
        }
        if (type != null) {
            questions = questions.stream().filter(q -> q.getQuestionType() == type).toList();
        }
        if (difficulty != null) {
            questions = questions.stream().filter(q -> q.getDifficulty() == difficulty).toList();
        }
        if (!includeInactive) {
            questions = questions.stream().filter(Question::isActive).toList();
        }

        return ResponseEntity.ok(questions.stream().map(this::toAdminQuestionResponse).toList());
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<AdminQuestionResponse> getQuestion(@PathVariable Long questionId) {
        return ResponseEntity.ok(toAdminQuestionResponse(questionService.getQuestionById(questionId)));
    }

    @PostMapping("/questions")
    public ResponseEntity<AdminQuestionResponse> createQuestion(
            @Valid @RequestBody QuestionCreateRequest request,
            UriComponentsBuilder uriBuilder) {

        Topic topic = topicService.getTopicById(request.topicId());
        Question question = Question.builder()
                .topic(topic)
                .statement(request.statement())
                .questionType(request.questionType())
                .difficulty(request.difficulty())
                .timeLimit(request.timeLimit())
                .score(request.score())
                .explanation(request.explanation())
                .build();

        AdminQuestionResponse body = switch (request.questionType()) {
            case MCQ -> {
                if (request.option() == null) {
                    throw new QuestionTypeMismatchException("MCQ question requires an \"option\" payload.");
                }
                Option option = Option.builder()
                        .correctOption(request.option().correctOption())
                        .option1(request.option().option1())
                        .option2(request.option().option2())
                        .option3(request.option().option3())
                        .option4(request.option().option4())
                        .build();
                questionService.createMCQQuestion(question, option);
                yield buildAdminQuestionResponse(question, option, null, List.of());
            }
            case CODING -> {
                if (request.codingChallenge() == null) {
                    throw new QuestionTypeMismatchException("CODING question requires a \"codingChallenge\" payload.");
                }
                CodingChallenge challenge = CodingChallenge.builder()
                        .starterCode(request.codingChallenge().starterCode())
                        .referenceSolution(request.codingChallenge().referenceSolution())
                        .build();
                List<TestCase> testCases = request.codingChallenge().testCases().stream()
                        .map(tc -> TestCase.builder()
                                .input(tc.input())
                                .expectedOutput(tc.expectedOutput())
                                .isHidden(tc.isHidden())
                                .build())
                        .toList();
                questionService.createCodingQuestion(question, challenge, testCases);
                yield buildAdminQuestionResponse(question, null, challenge, testCases);
            }
            case OPEN_ENDED -> {
                questionService.createOpenEndedQuestion(question);
                yield buildAdminQuestionResponse(question, null, null, List.of());
            }
        };

        return ResponseEntity
                .created(uriBuilder.path("/api/questions/{id}").buildAndExpand(question.getId()).toUri())
                .body(body);
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<Void> updateQuestion(
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionUpdateRequest request) {

        Topic topic = topicService.getTopicById(request.topicId());
        QuestionUpdateDTO dto = new QuestionUpdateDTO(
                topic, request.statement(), request.difficulty(), request.timeLimit(), request.explanation());
        questionService.updateQuestion(questionId, dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        questionService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/questions/{questionId}/option")
    public ResponseEntity<Void> updateOption(@PathVariable Long questionId, @RequestBody OptionUpdateDTO request) {
        Option existing = optionService.getOptionByQuestionId(questionId);
        optionService.updateOption(existing.getId(), request);
        return ResponseEntity.noContent().build();
    }

    // ---------- Coding challenges / test cases ----------

    @PutMapping("/coding-challenges/{challengeId}")
    public ResponseEntity<Void> updateCodingChallenge(
            @PathVariable Long challengeId,
            @RequestBody CodingChallengeUpdateDTO request) {

        codingChallengeService.updateCodingChallenge(challengeId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/coding-challenges/{challengeId}/test-cases")
    public ResponseEntity<AdminTestCaseResponse> createTestCase(
            @PathVariable Long challengeId,
            @Valid @RequestBody TestCaseCreateRequest request,
            UriComponentsBuilder uriBuilder) {

        CodingChallenge challenge = CodingChallenge.builder().id(challengeId).build();
        TestCase testCase = TestCase.builder()
                .codingChallenge(challenge)
                .input(request.input())
                .expectedOutput(request.expectedOutput())
                .isHidden(request.isHidden())
                .build();
        testCaseService.createTestCase(testCase);

        return ResponseEntity
                .created(uriBuilder.path("/api/admin/test-cases/{id}").buildAndExpand(testCase.getId()).toUri())
                .body(new AdminTestCaseResponse(
                        testCase.getId(), testCase.getInput(), testCase.getExpectedOutput(), testCase.isHidden()));
    }

    @PutMapping("/test-cases/{testCaseId}")
    public ResponseEntity<Void> updateTestCase(@PathVariable Long testCaseId, @RequestBody TestCaseUpdateDTO request) {
        testCaseService.updateTestcase(testCaseId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/test-cases/{testCaseId}")
    public ResponseEntity<Void> deleteTestCase(@PathVariable Long testCaseId) {
        testCaseService.deleteTestCaseById(testCaseId);
        return ResponseEntity.noContent().build();
    }

    // ---------- helpers ----------

    private void ensureNotSelf(Authentication authentication, Long targetUserId, String action) {
        User caller = userService.getUserByEmail(authentication.getName());
        if (caller.getId().equals(targetUserId)) {
            throw new AdminActionNotAllowedException("Admins cannot " + action + " their own account.");
        }
    }

    private UserProfileResponse toUserProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getWebName(),
                user.getEmail(),
                user.getRole(),
                user.getTargetRole(),
                user.isBanned(),
                user.isVerified(),
                user.getBanExpirationTime(),
                user.getCreatedAt()
        );
    }

    private TopicResponse toTopicResponse(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getTopicName(), topic.getDescription());
    }

    private AdminQuestionResponse toAdminQuestionResponse(Question question) {
        Option option = null;
        CodingChallenge challenge = null;
        List<TestCase> testCases = List.of();

        if (question.getQuestionType() == QuestionType.MCQ) {
            option = optionService.getOptionByQuestionId(question.getId());
        } else if (question.getQuestionType() == QuestionType.CODING) {
            challenge = question.getCodingChallenge();
            if (challenge != null) {
                testCases = testCaseService.getAllTestCases(challenge.getId());
            }
        }

        return buildAdminQuestionResponse(question, option, challenge, testCases);
    }

    private AdminQuestionResponse buildAdminQuestionResponse(
            Question question, Option option, CodingChallenge challenge, List<TestCase> testCases) {

        List<String> options = option != null
                ? Stream.of(option.getOption1(), option.getOption2(), option.getOption3(), option.getOption4())
                        .filter(Objects::nonNull).toList()
                : null;

        List<AdminTestCaseResponse> testCaseResponses = testCases.stream()
                .map(tc -> new AdminTestCaseResponse(tc.getId(), tc.getInput(), tc.getExpectedOutput(), tc.isHidden()))
                .toList();

        return new AdminQuestionResponse(
                question.getId(),
                question.getTopic() != null ? question.getTopic().getId() : null,
                question.getTopic() != null ? question.getTopic().getTopicName() : null,
                question.getStatement(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getTimeLimit(),
                question.getScore(),
                question.isActive(),
                question.getExplanation(),
                option != null ? option.getCorrectOption() : null,
                options,
                challenge != null ? challenge.getStarterCode() : null,
                challenge != null ? challenge.getReferenceSolution() : null,
                testCaseResponses
        );
    }
}
