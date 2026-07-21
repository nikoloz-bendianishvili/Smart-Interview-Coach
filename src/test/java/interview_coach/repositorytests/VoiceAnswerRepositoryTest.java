package interview_coach.repositorytests;

import interview_coach.entities.*;
import interview_coach.enums.*;
import interview_coach.InterviewCoachApplication;
import interview_coach.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class VoiceAnswerRepositoryTest {

    @Autowired
    private VoiceAnswerRepository voiceAnswerRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private SessionQuestionRepository sessionQuestionRepository;

    @Test
    void repositoryLoads() {
        assertThat(voiceAnswerRepository).isNotNull();
    }

    @Test
    void saveAndFindByAttemptAndStatus() {
        User u = User.builder().firstName("V").lastName("A").webName("u5").email("u5@example.com").passwordHash("p").role(Role.USER).isActive(true).build();
        userRepository.save(u);

        Topic t = Topic.builder().topicName("System Design").build();
        topicRepository.save(t);

        Session s = Session.builder()
                .user(u)
                .sessionType(SessionType.CUSTOM_PRACTICE)
                .status(SessionStatus.IN_PROGRESS)
                .build();
        sessionRepository.save(s);

        Question q = Question.builder()
                .topic(t)
                .statement("Explain CAP theorem")
                .questionType(QuestionType.OPEN_ENDED)
                .difficulty(Difficulty.EASY)
                .timeLimit(120)
                .build();
        questionRepository.save(q);

        SessionQuestion sq = SessionQuestion.builder()
                .session(s)
                .question(q)
                .orderIndex(1)
                .build();
        sessionQuestionRepository.save(sq);

        Attempt a = Attempt.builder().sessionQuestion(sq).user(u).build();
        attemptRepository.save(a);

        VoiceAnswer va = VoiceAnswer.builder()
                .attempt(a)
                .audioTranscript("hi")
                .gradingStatus(GradingStatus.PENDING)
                .build();
        voiceAnswerRepository.save(va);

        Optional<VoiceAnswer> found = voiceAnswerRepository.findByAttemptId(a.getId());
        List<VoiceAnswer> pending = voiceAnswerRepository.findByGradingStatus(GradingStatus.PENDING);

        assertThat(found).isPresent();
        assertThat(pending).isNotEmpty();
    }
}



