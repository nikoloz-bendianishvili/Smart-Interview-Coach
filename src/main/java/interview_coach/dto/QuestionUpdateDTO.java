package interview_coach.dto;

import interview_coach.entities.Topic;
import interview_coach.enums.Difficulty;

public record QuestionUpdateDTO(
        Topic topic,
        String statement,
        Difficulty difficulty,
        Integer timeLimit,
        String explanation
) {}
