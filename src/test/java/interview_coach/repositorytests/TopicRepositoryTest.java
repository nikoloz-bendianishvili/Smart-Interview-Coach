package interview_coach.repositorytests;

import interview_coach.entities.Topic;
import interview_coach.InterviewCoachApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.TopicRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class TopicRepositoryTest {

    @Autowired
    private TopicRepository topicRepository;

    @Test
    void repositoryLoads() {
        assertThat(topicRepository).isNotNull();
    }

    @Test
    void saveAndFind() {
        Topic t = Topic.builder()
                .topicName("Java")
                .description("Java core topics")
                .build();

        topicRepository.save(t);

        Topic found = topicRepository.findById(t.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getTopicName()).isEqualTo("Java");
    }
}



