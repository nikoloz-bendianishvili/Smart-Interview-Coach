package interview_coach.repositorytests;

import interview_coach.entities.CodingChallenge;
import interview_coach.entities.Question;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import interview_coach.entities.Topic;
import interview_coach.InterviewCoachApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.CodingChallengeRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.TopicRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class CodingChallengeRepositoryTest {

    @Autowired
    private CodingChallengeRepository codingChallengeRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Test
    void repositoryLoads() {
        assertThat(codingChallengeRepository).isNotNull();
    }

    @Test
    void saveAndFindByQuestion() {
        Topic t = Topic.builder().topicName("CC").description("cc").build();
        topicRepository.save(t);

        Question q = Question.builder()
                .topic(t)
                .statement("Solve X")
                .questionType(QuestionType.CODING)
                .difficulty(Difficulty.MEDIUM)
                .timeLimit(300)
                .build();
        questionRepository.save(q);

        CodingChallenge cc = CodingChallenge.builder()
                .questionId(q)
                .starterCode("// start")
                .referenceSolution("// solution")
                .build();

        codingChallengeRepository.save(cc);

        Optional<CodingChallenge> found = codingChallengeRepository.findByQuestionId(q);
        assertThat(found).isPresent();
    }
}



