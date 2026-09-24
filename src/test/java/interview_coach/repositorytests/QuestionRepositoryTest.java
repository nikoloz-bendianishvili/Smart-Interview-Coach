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

import java.util.List;

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

    @Test
    void findRandomByType_respectsLimitAndExcludesSoftDeletedQuestions() {
        Topic t = Topic.builder().topicName("Randomization").description("rand").build();
        topicRepository.save(t);

        for (int i = 0; i < 5; i++) {
            questionRepository.save(Question.builder()
                    .topic(t)
                    .statement("Active MCQ " + i)
                    .questionType(QuestionType.MCQ)
                    .difficulty(Difficulty.EASY)
                    .timeLimit(60)
                    .build());
        }
        questionRepository.save(Question.builder()
                .topic(t)
                .statement("Inactive MCQ")
                .questionType(QuestionType.MCQ)
                .difficulty(Difficulty.EASY)
                .timeLimit(60)
                .active(false)
                .build());

        List<Question> result = questionRepository.findRandomByType(QuestionType.MCQ.name(), 3);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Question::getStatement).doesNotContain("Inactive MCQ");
    }

    @Test
    void findRandomByTopicAndType_respectsLimitTopicAndTypeAndExcludesSoftDeletedQuestions() {
        Topic target = Topic.builder().topicName("Target Topic").description("target").build();
        Topic other = Topic.builder().topicName("Other Topic").description("other").build();
        topicRepository.save(target);
        topicRepository.save(other);

        for (int i = 0; i < 4; i++) {
            questionRepository.save(Question.builder()
                    .topic(target)
                    .statement("Target CODING " + i)
                    .questionType(QuestionType.CODING)
                    .difficulty(Difficulty.MEDIUM)
                    .timeLimit(300)
                    .build());
        }
        // Wrong topic - must never be returned.
        questionRepository.save(Question.builder()
                .topic(other)
                .statement("Other topic CODING")
                .questionType(QuestionType.CODING)
                .difficulty(Difficulty.MEDIUM)
                .timeLimit(300)
                .build());
        // Right topic, wrong type - must never be returned.
        questionRepository.save(Question.builder()
                .topic(target)
                .statement("Target MCQ")
                .questionType(QuestionType.MCQ)
                .difficulty(Difficulty.MEDIUM)
                .timeLimit(60)
                .build());
        // Right topic and type, but soft-deleted - must never be returned.
        questionRepository.save(Question.builder()
                .topic(target)
                .statement("Target CODING inactive")
                .questionType(QuestionType.CODING)
                .difficulty(Difficulty.MEDIUM)
                .timeLimit(300)
                .active(false)
                .build());

        List<Question> result = questionRepository.findRandomByTopicAndType(target.getId(), QuestionType.CODING.name(), 2);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(q -> {
            assertThat(q.getTopic().getId()).isEqualTo(target.getId());
            assertThat(q.getQuestionType()).isEqualTo(QuestionType.CODING);
            assertThat(q.isActive()).isTrue();
        });
    }
}



