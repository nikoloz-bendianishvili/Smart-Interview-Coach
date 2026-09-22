package interview_coach.dto;

public record AdminTestCaseResponse(
        Long id,
        String input,
        String expectedOutput,
        boolean isHidden
) {}
