package interview_coach.dto;

import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;

import java.util.List;

public record SessionQuestionResponse(
        Long sessionQuestionId,
        int orderIndex,
        QuestionType questionType,
        Difficulty difficulty,
        String topicName,
        String statement,
        Integer timeLimit,

        // MCQ only
        List<String> options,          // option1-4, non-null values only, no correctOption

        // Coding only
        String starterCode,
        List<TestCaseResponse> visibleTestCases
) {}
