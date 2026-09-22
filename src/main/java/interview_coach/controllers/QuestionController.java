package interview_coach.controllers;

import interview_coach.dto.QuestionResponse;
import interview_coach.dto.TestCaseResponse;
import interview_coach.entities.CodingChallenge;
import interview_coach.entities.Option;
import interview_coach.entities.Question;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import interview_coach.exceptions.QuestionNotFoundException;
import interview_coach.services.core.OptionService;
import interview_coach.services.core.QuestionService;
import interview_coach.services.core.TestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Public, read-only. Every route here is permitAll in SecurityConfig
 * (GET /api/questions/**), so responses must never leak the answer key
 * (Option.correctOption, CodingChallenge.referenceSolution), hidden
 * TestCases, Question.explanation, or soft-deleted (inactive) questions.
 */
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final OptionService optionService;
    private final TestCaseService testCaseService;

    @GetMapping
    public ResponseEntity<List<QuestionResponse>> getQuestions(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) Difficulty difficulty) {

        List<Question> questions;
        if (topicId != null && type != null) {
            questions = questionService.getByTopicIdAndQuestionType(topicId, type);
        } else if (topicId != null) {
            questions = questionService.getQuestionsByTopicId(topicId);
        } else if (type != null) {
            questions = questionService.getByQuestionType(type);
        } else if (difficulty != null) {
            questions = questionService.getByDifficulty(difficulty);
        } else {
            questions = questionService.getAllQuestions().stream()
                    .filter(Question::isActive)
                    .toList();
        }

        return ResponseEntity.ok(questions.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<QuestionResponse> getQuestion(@PathVariable Long questionId) {
        Question question = questionService.getQuestionById(questionId);
        if (!question.isActive()) {
            throw new QuestionNotFoundException("Question not found with id: " + questionId);
        }
        return ResponseEntity.ok(toResponse(question));
    }

    private QuestionResponse toResponse(Question question) {
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

        return new QuestionResponse(
                question.getId(),
                question.getTopic() != null ? question.getTopic().getId() : null,
                question.getTopic() != null ? question.getTopic().getTopicName() : null,
                question.getStatement(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getTimeLimit(),
                question.getScore(),
                options,
                starterCode,
                visibleTestCases
        );
    }
}
