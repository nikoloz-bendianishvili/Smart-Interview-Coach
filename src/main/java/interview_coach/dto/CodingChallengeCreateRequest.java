package interview_coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;

import java.util.List;

public record CodingChallengeCreateRequest(
        @NotBlank String starterCode,
        String referenceSolution,
        @NotEmpty @Valid List<TestCaseCreateRequest> testCases
) {}
