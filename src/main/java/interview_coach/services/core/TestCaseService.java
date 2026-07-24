package interview_coach.services.core;

import interview_coach.dto.TestCaseUpdateDTO;
import interview_coach.entities.CodingChallenge;
import interview_coach.entities.TestCase;
import interview_coach.exceptions.CodingChallengeNotFound;
import interview_coach.exceptions.TestCaseNotFoundException;
import interview_coach.repositories.CodingChallengeRepository;
import interview_coach.repositories.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final CodingChallengeRepository codingChallengeRepository;

    @Transactional
    public void deleteTestCaseById(Long id) {
        testCaseRepository.deleteById(id);
    }

    @Transactional
    public void updateTestcase(Long testCaseId, TestCaseUpdateDTO testCaseUpdateDTO) {
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new TestCaseNotFoundException("Test case not found with id: " + testCaseId));

        testCase.setInput(testCaseUpdateDTO.input());
        testCase.setExpectedOutput(testCaseUpdateDTO.expectedOutput());
        testCase.setHidden(testCaseUpdateDTO.isHidden());
        testCaseRepository.save(testCase);
    }

    @Transactional
    public void createTestCase(TestCase testCase) {
        if(!codingChallengeRepository.existsById(testCase.getCodingChallenge().getId())) {
            throw new CodingChallengeNotFound("Invalid coding challenge for the test case.");
        }
        testCaseRepository.save(testCase);
    }

    public List<TestCase> getVisibleTestCases(Long challengeId) {
        return testCaseRepository.findByCodingChallengeIdAndIsHidden(challengeId, false);
    }

    public List<TestCase> getAllTestCases(Long challengeId) {
        return testCaseRepository.findByCodingChallengeId(challengeId);
    }
}
