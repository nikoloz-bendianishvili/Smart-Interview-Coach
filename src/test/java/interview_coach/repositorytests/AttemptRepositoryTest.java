package interview_coach.repositorytests;

import interview_coach.entities.Attempt;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.Session;
import interview_coach.entities.User;
import interview_coach.entities.Question;
import interview_coach.entities.Topic;
import interview_coach.enums.*;
import interview_coach.InterviewCoachApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.SessionQuestionRepository;
import interview_coach.repositories.SessionRepository;
import interview_coach.repositories.UserRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.TopicRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class AttemptRepositoryTest {

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private SessionQuestionRepository sessionQuestionRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Test
    void repositoryLoads() {
        assertThat(attemptRepository).isNotNull();
    }

    @Test
    void saveAndFindByUser() {
        User u = User.builder().firstName("A").lastName("B").webName("u3").email("u3@example.com").passwordHash("p").role(Role.USER).isActive(true).build();
        userRepository.save(u);

        Session s = Session.builder()
                .user(u)
                .sessionType(SessionType.CUSTOM_PRACTICE)
                .status(SessionStatus.IN_PROGRESS)
                .build();
        sessionRepository.save(s);
        sessionRepository.save(s);

        Topic t = Topic.builder().topicName("T2").build();
        topicRepository.save(t);

        Question q = Question.builder()
                .topic(t)
                .statement("q")
                .questionType(QuestionType.MCQ)
                .difficulty(Difficulty.EASY)
                .timeLimit(5)
                .build();
        questionRepository.save(q);

        SessionQuestion sq = SessionQuestion.builder().session(s).question(q).orderIndex(1).build();
        sessionQuestionRepository.save(sq);

        Attempt a = Attempt.builder().sessionQuestion(sq).user(u).selectedOption(1).isCorrect(true).score(10).timeTakenSeconds(20).build();
        attemptRepository.save(a);

        assertThat(attemptRepository.findByUserId(u.getId())).isNotEmpty();
    }
}



