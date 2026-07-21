package interview_coach.repositorytests;

import interview_coach.entities.Attempt;
import interview_coach.entities.CodeSubmission;
import interview_coach.entities.Session;
import interview_coach.entities.User;
import interview_coach.entities.Topic;
import interview_coach.entities.Question;
import interview_coach.entities.SessionQuestion;
import interview_coach.enums.Role;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import interview_coach.InterviewCoachApplication;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.SessionRepository;
import interview_coach.repositories.UserRepository;
import interview_coach.repositories.TopicRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.SessionQuestionRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class CodeSubmissionRepositoryTest {

    @Autowired
    private CodeSubmissionRepository codeSubmissionRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private SessionQuestionRepository sessionQuestionRepository;

    @Test
    void repositoryLoads() {
        assertThat(codeSubmissionRepository).isNotNull();
    }

    @Test
    void saveAndFindByAttempt() {
        User u = User.builder().firstName("A").lastName("B").webName("u4").email("u4@example.com").passwordHash("p").role(Role.USER).isActive(true).build();
        userRepository.save(u);

        Session s = Session.builder()
                .user(u)
                .sessionType(SessionType.CUSTOM_PRACTICE)
                .status(SessionStatus.IN_PROGRESS)
                .build();
        sessionRepository.save(s);
        sessionRepository.save(s);

        Topic t = Topic.builder().topicName("T-test").build();
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

        Attempt a = Attempt.builder().sessionQuestion(sq).user(u).build();
        attemptRepository.save(a);

        CodeSubmission cs = CodeSubmission.builder().attempt(a).sourceCode("//code").passedTestCount(1).totalTestCount(1).build();
        codeSubmissionRepository.save(cs);

        Optional<CodeSubmission> found = codeSubmissionRepository.findByAttemptId(a.getId());
        assertThat(found).isPresent();
    }
}



