package interview_coach.repositorytests;

import interview_coach.entities.CodingChallenge;
import interview_coach.entities.TestCase;
import interview_coach.entities.Topic;
import interview_coach.entities.Question;
import interview_coach.InterviewCoachApplication;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.CodingChallengeRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.TestCaseRepository;
import interview_coach.repositories.TopicRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class TestCaseRepositoryTest {

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private CodingChallengeRepository codingChallengeRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Test
    void repositoryLoads() {
        assertThat(testCaseRepository).isNotNull();
    }

    @Test
    void saveAndFindByCodingChallenge() {
        Topic t = Topic.builder().topicName("TC").description("tc").build();
        topicRepository.save(t);

        Question q = Question.builder().topic(t).statement("Q").questionType(QuestionType.CODING).difficulty(Difficulty.MEDIUM).timeLimit(60).build();
        questionRepository.save(q);

        CodingChallenge cc = CodingChallenge.builder().questionId(q).starterCode("//").build();
        codingChallengeRepository.save(cc);

        TestCase tc = TestCase.builder().codingChallenge(cc).input("1\n").expectedOutput("1\n").isHidden(false).build();
        testCaseRepository.save(tc);

        List<TestCase> list = testCaseRepository.findByCodingChallengeId(cc.getId());
        assertThat(list).isNotEmpty();
    }
}



