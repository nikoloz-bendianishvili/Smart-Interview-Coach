package interview_coach.repositorytests;

import interview_coach.entities.Question;
import interview_coach.entities.Topic;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import interview_coach.InterviewCoachApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.TopicRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class QuestionRepositoryTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Test
    void repositoryLoads() {
        assertThat(questionRepository).isNotNull();
    }

    @Test
    void saveAndFindByTopicAndType() {
        Topic t = Topic.builder().topicName("DSA").description("algorithms").build();
        topicRepository.save(t);

        Question q = Question.builder()
                .topic(t)
                .statement("What is quicksort?")
                .questionType(QuestionType.OPEN_ENDED)
                .difficulty(Difficulty.MEDIUM)
                .timeLimit(120)
                .build();

        questionRepository.save(q);

        assertThat(questionRepository.findByTopicId(t.getId())).isNotEmpty();
        assertThat(questionRepository.findByQuestionType(QuestionType.OPEN_ENDED)).isNotEmpty();
    }
}



