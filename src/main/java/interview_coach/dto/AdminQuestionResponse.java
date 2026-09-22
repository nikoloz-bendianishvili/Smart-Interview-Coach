package interview_coach.dto;

import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;

import java.util.List;

/**
 * Admin-only view of a Question - the only way to browse the raw question bank at all, since
 * there is no public/general question-browsing endpoint. Includes the answer key
 * (correctOption / referenceSolution), all test cases (including hidden ones), the explanation,
 * and the active flag.
 */
public record AdminQuestionResponse(
        Long id,
        Long topicId,
        String topicName,
        String statement,
        QuestionType questionType,
        Difficulty difficulty,
        Integer timeLimit,
        Integer score,
        boolean active,
        String explanation,
        Integer correctOption,
        List<String> options,
        String starterCode,
        String referenceSolution,
        List<AdminTestCaseResponse> testCases
) {}
