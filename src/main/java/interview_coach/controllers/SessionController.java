package interview_coach.controllers;


import interview_coach.dto.*;
import interview_coach.entities.*;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.repositories.VoiceAnswerRepository;
import interview_coach.services.core.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Tag(name = "Sessions", description = "Start and complete mock-interview sessions (CUSTOM_PRACTICE, " +
        "REAL_INTERVIEW, FREE_MOCK).")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final UserService userService;
    private final TopicService topicService;
    private final OptionService optionService;
    private final TestCaseService testCaseService;
    private final VoiceAnswerRepository voiceAnswerRepository;

    @Operation(summary = "Start a session", description = "Question selection depends on sessionType: " +
            "CUSTOM_PRACTICE needs topicId + questionType + numOfQuestions; REAL_INTERVIEW derives the " +
            "question count from timeLimitMinutes; FREE_MOCK needs exactly one of numOfQuestions or " +
            "timeLimitMinutes. REAL_INTERVIEW is answered strictly in order, so only the first question " +
            "is included here - fetch the rest one at a time via GET /{sessionId}/current-question as " +
            "each one is answered or given up. The other two types get every question up front.")
    @PostMapping("/start")
    public ResponseEntity<SessionStartResponse> startSession(
            Authentication authentication,
            @RequestBody StartSessionRequest request) {

        User user = userService.getUserByEmail(authentication.getName());
        Topic topic = request.topicId() != null ? topicService.getTopicById(request.topicId()) : null;

        SessionDTO sessionDTO = new SessionDTO(
                user, topic, request.sessionType(), request.questionType(),
                request.interactionMode(), request.numOfQuestions(), request.timeLimitMinutes()
        );

        Session session = sessionService.startSession(sessionDTO);
        List<SessionQuestion> sessionQuestions = sessionService.getSessionQuestions(session.getId());

        // REAL_INTERVIEW hands out one question at a time (see getCurrentQuestion) rather than
        // revealing the whole ordered set up front; the other two types are unordered, so there's
        // nothing to gain by withholding them.
        List<SessionQuestion> questionsToReturn = session.getSessionType() == SessionType.REAL_INTERVIEW
                ? sessionQuestions.subList(0, Math.min(1, sessionQuestions.size()))
                : sessionQuestions;

        List<SessionQuestionResponse> sessionQuestionResponses = questionsToReturn.stream()
                .map(this::toSessionQuestionResponse)
                .toList();

        SessionStartResponse response = new SessionStartResponse(
                session.getId(),
                session.getSessionType(),
                sessionQuestions.size(),
                sessionQuestionResponses
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get the current question", description = "The lowest orderIndex question in " +
            "this session that hasn't been answered or given up yet. This is how a REAL_INTERVIEW " +
            "client fetches question 2, 3, ... one at a time, since startSession only ever hands it " +
            "question 1. Works for any session type, though FREE_MOCK/CUSTOM_PRACTICE clients don't " +
            "need it - they already got every question from startSession. 404 once every question in " +
            "the session has been dealt with.")
    @GetMapping("/{sessionId}/current-question")
    public ResponseEntity<SessionQuestionResponse> getCurrentQuestion(
            Authentication authentication,
            @PathVariable Long sessionId) {

        User user = userService.getUserByEmail(authentication.getName());
        SessionQuestion sq = sessionService.getCurrentQuestion(sessionId, user);

        return ResponseEntity.ok(toSessionQuestionResponse(sq));
    }

    private SessionQuestionResponse toSessionQuestionResponse(SessionQuestion sq) {
        Question question = sq.getQuestion();

        List<String> options = null;
        String starterCode = null;
        List<TestCaseResponse> visibleTestCases = null;

        if (question.getQuestionType() == QuestionType.MCQ) {
            Option option = optionService.getOptionByQuestionId(question.getId());
            options = Stream.of(option.getOption1(), option.getOption2(), option.getOption3(), option.getOption4())
                    .filter(Objects::nonNull)
                    .toList();
        } else if (question.getQuestionType() == QuestionType.CODING) {
            CodingChallenge challenge = question.getCodingChallenge();
            starterCode = challenge.getStarterCode();
            visibleTestCases = testCaseService.getVisibleTestCases(challenge.getId()).stream()
                    .map(tc -> new TestCaseResponse(tc.getInput(), tc.getExpectedOutput()))
                    .toList();
        }

        return new SessionQuestionResponse(
                sq.getId(),
                sq.getOrderIndex(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getTopic() != null ? question.getTopic().getTopicName() : null,
                question.getStatement(),
                question.getTimeLimit(),
                options,
                starterCode,
                visibleTestCases
        );
    }

    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<SessionSummaryResponse> completeSession(
            Authentication authentication,
            @PathVariable Long sessionId) {

        User user = userService.getUserByEmail(authentication.getName());
        Session session = sessionService.completeSession(sessionId, user);
        SessionSummaryResponse response = buildSummaryResponse(session);

        // 202 while grading is still catching up (AWAITING_GRADING), 200 once fully scored -
        // mirrors the 201/202 split already used for MCQ vs. async attempt submission.
        return session.getStatus() == SessionStatus.AWAITING_GRADING
                ? ResponseEntity.accepted().body(response)
                : ResponseEntity.ok(response);
    }

    @Operation(summary = "Get session status/summary", description = "Ownership-checked read of a " +
            "session's current status and score. Also runs the same deadline check every mutating " +
            "route does, so polling a session nobody has touched since it expired still reflects the " +
            "up-to-date state rather than a stale IN_PROGRESS. Returns 202 while AWAITING_GRADING, " +
            "200 once fully scored - this is how a client finds out an AWAITING_GRADING session it " +
            "got back from /complete has since finished grading.")
    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionSummaryResponse> getSession(
            Authentication authentication,
            @PathVariable Long sessionId) {

        User user = userService.getUserByEmail(authentication.getName());
        Session session = sessionService.getSessionForUser(sessionId, user);
        SessionSummaryResponse response = buildSummaryResponse(session);

        return session.getStatus() == SessionStatus.AWAITING_GRADING
                ? ResponseEntity.accepted().body(response)
                : ResponseEntity.ok(response);
    }

    private SessionSummaryResponse buildSummaryResponse(Session session) {
        Long sessionId = session.getId();
        List<Attempt> attempts = attemptService.getAttemptsBySession(sessionId);

        int maxScore = 0;
        for (SessionQuestion sq : sessionService.getSessionQuestions(sessionId)) {
            Integer questionMaxScore = sq.getQuestion().getScore();
            maxScore += questionMaxScore != null ? questionMaxScore : 0;
        }

        List<AttemptSummaryResponse> results = new ArrayList<>();
        for (Attempt attempt : attempts) {
            Question question = attempt.getSessionQuestion().getQuestion();
            Integer questionMaxScore = question.getScore();

            String aiFeedback = null;
            if (question.getQuestionType() == QuestionType.OPEN_ENDED) {
                aiFeedback = voiceAnswerRepository.findByAttemptId(attempt.getId())
                        .map(VoiceAnswer::getAiFeedback)
                        .orElse(null);
            }

            results.add(new AttemptSummaryResponse(
                    attempt.getSessionQuestion().getId(),
                    attempt.getSessionQuestion().getOrderIndex(),
                    question.getQuestionType(),
                    question.getStatement(),
                    attempt.getStatus(),
                    attempt.getScore(),
                    questionMaxScore,
                    question.getExplanation(),
                    aiFeedback
            ));
        }

        return new SessionSummaryResponse(
                session.getId(),
                session.getSessionType(),
                session.getStatus(),
                session.getTotalScore(),
                maxScore,
                session.getStartTime(),
                session.getEndTime(),
                results
        );
    }
}
