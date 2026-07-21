package interview_coach.repositorytests;

import interview_coach.entities.Session;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.Topic;
import interview_coach.entities.User;
import interview_coach.entities.Question;
import interview_coach.enums.*;
import interview_coach.InterviewCoachApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.SessionQuestionRepository;
import interview_coach.repositories.SessionRepository;
import interview_coach.repositories.UserRepository;
import interview_coach.repositories.TopicRepository;
import interview_coach.repositories.QuestionRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class SessionQuestionRepositoryTest {

    @Autowired
    private SessionQuestionRepository sessionQuestionRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void repositoryLoads() {
        assertThat(sessionQuestionRepository).isNotNull();
    }

    @Test
    void saveAndFindBySession() {
        User u = User.builder().firstName("U").lastName("L").webName("u2").email("u2@example.com").passwordHash("p").role(Role.USER).isActive(true).build();
        userRepository.save(u);

        Session s = Session.builder()
                .user(u)
                .sessionType(SessionType.CUSTOM_PRACTICE)
                .status(SessionStatus.IN_PROGRESS)
                .build();
        sessionRepository.save(s);

        Topic t = Topic.builder().topicName("T").build();
        topicRepository.save(t);

        Question q = Question.builder().topic(t).statement("s").questionType(QuestionType.OPEN_ENDED).difficulty(Difficulty.MEDIUM).timeLimit(10).build();
        questionRepository.save(q);

        SessionQuestion sq = SessionQuestion.builder().session(s).question(q).orderIndex(1).build();
        sessionQuestionRepository.save(sq);

        assertThat(sessionQuestionRepository.findBySessionIdOrderByOrderIndexAsc(s.getId())).isNotEmpty();
    }
}



