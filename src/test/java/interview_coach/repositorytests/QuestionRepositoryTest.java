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

    @Test
    void countByTopicIdCountsOnlyThatTopicsQuestions() {
        Topic t1 = Topic.builder().topicName("Algorithms").description("algo").build();
        Topic t2 = Topic.builder().topicName("Databases").description("db").build();
        topicRepository.save(t1);
        topicRepository.save(t2);

        Question q = Question.builder()
                .topic(t1)
                .statement("What is a hash map?")
                .questionType(QuestionType.OPEN_ENDED)
                .difficulty(Difficulty.EASY)
                .timeLimit(60)
                .build();
        questionRepository.save(q);

        assertThat(questionRepository.countByTopicId(t1.getId())).isEqualTo(1L);
        assertThat(questionRepository.countByTopicId(t2.getId())).isEqualTo(0L);
    }

    @Test
    void activeTrueFindersExcludeSoftDeletedQuestions() {
        Topic t = Topic.builder().topicName("Networking").description("net").build();
        topicRepository.save(t);

        Question active = Question.builder()
                .topic(t)
                .statement("What is TCP?")
                .questionType(QuestionType.OPEN_ENDED)
                .difficulty(Difficulty.MEDIUM)
                .timeLimit(90)
                .build();
        Question inactive = Question.builder()
                .topic(t)
                .statement("What is UDP?")
                .questionType(QuestionType.OPEN_ENDED)
                .difficulty(Difficulty.MEDIUM)
                .timeLimit(90)
                .active(false)
                .build();
        questionRepository.save(active);
        questionRepository.save(inactive);

        assertThat(questionRepository.findByTopicIdAndActiveTrue(t.getId()))
                .extracting(Question::getStatement)
                .containsExactly("What is TCP?");
        assertThat(questionRepository.findByQuestionTypeAndActiveTrue(QuestionType.OPEN_ENDED))
                .extracting(Question::getStatement)
                .contains("What is TCP?")
                .doesNotContain("What is UDP?");
    }
}



