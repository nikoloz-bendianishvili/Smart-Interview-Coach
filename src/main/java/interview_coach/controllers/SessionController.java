package interview_coach.controllers;


import interview_coach.dto.*;
import interview_coach.entities.*;
import interview_coach.enums.QuestionType;
import interview_coach.repositories.VoiceAnswerRepository;
import interview_coach.services.core.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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

        List<SessionQuestionResponse> sessionQuestionResponses = new ArrayList<>();
        for (SessionQuestion sq : sessionQuestions) {
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

            sessionQuestionResponses.add(new SessionQuestionResponse(
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
            ));
        }

        SessionStartResponse response = new SessionStartResponse(
                session.getId(),
                session.getSessionType(),
                sessionQuestions.size(),
                sessionQuestionResponses
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<SessionSummaryResponse> completeSession(
            Authentication authentication,
            @PathVariable Long sessionId) {

        User user = userService.getUserByEmail(authentication.getName());
        Session session = sessionService.completeSession(sessionId, user);
        List<Attempt> attempts = attemptService.getAttemptsBySession(sessionId);

        List<AttemptSummaryResponse> results = new ArrayList<>();
        int maxScore = 0;
        for (Attempt attempt : attempts) {
            Question question = attempt.getSessionQuestion().getQuestion();
            Integer questionMaxScore = question.getScore();
            maxScore += questionMaxScore != null ? questionMaxScore : 0;

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

        SessionSummaryResponse response = new SessionSummaryResponse(
                session.getId(),
                session.getSessionType(),
                session.getStatus(),
                session.getTotalScore(),
                maxScore,
                session.getStartTime(),
                session.getEndTime(),
                results
        );

        return ResponseEntity.ok(response);
    }
}
