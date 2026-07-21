package interview_coach.repositorytests;

import interview_coach.entities.Option;
import interview_coach.entities.Question;
import interview_coach.entities.Topic;
import interview_coach.InterviewCoachApplication;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.OptionRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.TopicRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class OptionRepositoryTest {

    @Autowired
    private OptionRepository optionRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Test
    void repositoryLoads() {
        assertThat(optionRepository).isNotNull();
    }

    @Test
    void saveAndFindByQuestion() {
        Topic t = Topic.builder().topicName("MCQ").description("mcq").build();
        topicRepository.save(t);

        Question q = Question.builder()
                .topic(t)
                .statement("Choose one")
                .questionType(QuestionType.MCQ)
                .difficulty(Difficulty.EASY)
                .timeLimit(30)
                .build();
        questionRepository.save(q);

        Option o = Option.builder()
                .questionId(q)
                .correctOption(1)
                .option1("A")
                .option2("B")
                .option3("C")
                .option4("D")
                .build();

        optionRepository.save(o);

        assertThat(optionRepository.findByQuestionId(q)).isPresent();
    }
}



