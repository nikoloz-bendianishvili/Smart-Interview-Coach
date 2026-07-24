package interview_coach.dto;

import interview_coach.entities.Topic;
import interview_coach.entities.User;
import interview_coach.enums.InteractionMode;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionType;

public record SessionDTO(
        User user,
        Topic topic,
        SessionType sessionType,
        QuestionType questionType,
        InteractionMode interactionMode,
        Integer numOfQuestions,
        Integer timeLimitInMinutes
) {}
