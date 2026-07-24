package interview_coach.dto;

public record TestCaseUpdateDTO(
       String input,
       String expectedOutput,
       boolean isHidden
) {}
