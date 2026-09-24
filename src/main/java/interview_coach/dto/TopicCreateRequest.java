package interview_coach.dto;

import jakarta.validation.constraints.NotBlank;

public record TopicCreateRequest(
        @NotBlank String topicName,
        String description
) {}
