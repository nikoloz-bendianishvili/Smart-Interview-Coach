package interview_coach.controllers;

import interview_coach.dto.*;
import interview_coach.entities.Attempt;
import interview_coach.entities.CodeSubmission;
import interview_coach.entities.User;
import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.GradingStatus;
import interview_coach.enums.QuestionType;
import interview_coach.services.core.AttemptService;
import interview_coach.services.core.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.VoiceAnswerRepository;

@Tag(name = "Attempts", description = "Submit answers to session questions and poll grading status. " +
        "MCQ grades instantly (201); code and open-ended answers grade asynchronously (202) and are " +
        "polled via GET /{attemptId}.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/attempts")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;
    private final UserService userService;
    private final CodeSubmissionRepository codeSubmissionRepository;
    private final VoiceAnswerRepository voiceAnswerRepository;

    @PostMapping("/{sessionQuestionId}/mcq")
    public ResponseEntity<AttemptResponse> submitMcq(
            Authentication authentication,
            @PathVariable Long sessionQuestionId,
            @Valid @RequestBody McqAttemptRequest request,
            UriComponentsBuilder uriBuilder) {

        User user = userService.getUserByEmail(authentication.getName());
        Attempt attempt = attemptService.submitMCQAttempt(
                sessionQuestionId, user, request.selectedOption(), request.timeTakenSeconds());

        return ResponseEntity
                .created(uriBuilder.path("/api/attempts/{id}").buildAndExpand(attempt.getId()).toUri())
                .body(toResponse(attempt));
    }

    @PostMapping("/{sessionQuestionId}/code")
    public ResponseEntity<AttemptResponse> submitCode(
            Authentication authentication,
            @PathVariable Long sessionQuestionId,
            @Valid @RequestBody CodeAttemptRequest request,
            UriComponentsBuilder uriBuilder) {

        User user = userService.getUserByEmail(authentication.getName());
        Attempt attempt = attemptService.submitCodingAttempt(
                sessionQuestionId, user, request.sourceCode(), request.timeTakenSeconds());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .location(uriBuilder.path("/api/attempts/{id}").buildAndExpand(attempt.getId()).toUri())
                .body(toResponse(attempt));
    }

    @PostMapping("/{sessionQuestionId}/answer")
    public ResponseEntity<AttemptResponse> submitOpenEnded(
            Authentication authentication,
            @PathVariable Long sessionQuestionId,
            @Valid @RequestBody OpenEndedAttemptRequest request,
            UriComponentsBuilder uriBuilder) {

        User user = userService.getUserByEmail(authentication.getName());
        Attempt attempt = attemptService.submitOpenEndedAttempt(
                sessionQuestionId, user, request.answerText(), request.timeTakenSeconds());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .location(uriBuilder.path("/api/attempts/{id}").buildAndExpand(attempt.getId()).toUri())
                .body(toResponse(attempt));
    }

    @PostMapping("/{sessionQuestionId}/give-up")
    public ResponseEntity<GiveUpResponse> giveUp(
            Authentication authentication,
            @PathVariable Long sessionQuestionId,
            UriComponentsBuilder uriBuilder) {

        User user = userService.getUserByEmail(authentication.getName());
        AttemptService.GiveUpResult result = attemptService.giveUpAttempt(sessionQuestionId, user);
        Attempt attempt = result.attempt();

        GiveUpResponse response = new GiveUpResponse(
                attempt.getId(),
                sessionQuestionId,
                attempt.getStatus(),
                result.explanation()
        );

        return ResponseEntity
                .created(uriBuilder.path("/api/attempts/{id}").buildAndExpand(attempt.getId()).toUri())
                .body(response);
    }

    @Operation(summary = "Poll attempt/grading status", description = "The Location header on the " +
            "three POST-create routes points here. Use this to poll gradingStatus for CODING/OPEN_ENDED " +
            "attempts until it leaves PENDING.")
    @GetMapping("/{attemptId}")
    public ResponseEntity<AttemptResponse> getAttempt(
            Authentication authentication,
            @PathVariable Long attemptId) {

        User user = userService.getUserByEmail(authentication.getName());
        Attempt attempt = attemptService.getAttemptForUser(attemptId, user);

        return ResponseEntity.ok(toResponse(attempt));
    }

    private AttemptResponse toResponse(Attempt attempt) {
        QuestionType questionType = attempt.getSessionQuestion().getQuestion().getQuestionType();

        Integer passedTestCount = null;
        Integer totalTestCount = null;
        Double aiScore = null;
        String aiFeedback = null;
        GradingStatus gradingStatus = null;

        if (questionType == QuestionType.CODING) {
            CodeSubmission submission = codeSubmissionRepository.findByAttemptId(attempt.getId()).orElse(null);
            if (submission != null) {
                passedTestCount = submission.getPassedTestCount();
                totalTestCount = submission.getTotalTestCount();
                gradingStatus = submission.getStatus();
            }
        } else if (questionType == QuestionType.OPEN_ENDED) {
            VoiceAnswer voiceAnswer = voiceAnswerRepository.findByAttemptId(attempt.getId()).orElse(null);
            if (voiceAnswer != null) {
                aiScore = voiceAnswer.getAiScore();
                aiFeedback = voiceAnswer.getAiFeedback();
                gradingStatus = voiceAnswer.getGradingStatus();
            }
        }

        return new AttemptResponse(
                attempt.getId(),
                attempt.getSessionQuestion().getId(),
                questionType,
                attempt.getStatus(),
                attempt.getScore(),
                attempt.getTimeTakenSeconds(),
                attempt.getIsCorrect(),
                gradingStatus,
                passedTestCount,
                totalTestCount,
                aiScore,
                aiFeedback
        );
    }
}