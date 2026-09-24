package interview_coach.servicetests;

import interview_coach.InterviewCoachApplication;
import interview_coach.entities.Attempt;
import interview_coach.entities.Question;
import interview_coach.entities.Session;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.Topic;
import interview_coach.entities.User;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import interview_coach.enums.Role;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.SessionQuestionRepository;
import interview_coach.repositories.SessionRepository;
import interview_coach.repositories.TopicRepository;
import interview_coach.repositories.UserRepository;
import interview_coach.services.core.SessionSweepService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the real cross-repository behavior a Mockito unit test can only assert past: a stale
 * PENDING attempt marked FAILED lets its AWAITING_GRADING session finalize to COMPLETED in the
 * very same sweep() call, because staleness-marking runs before the orphan-finalization pass
 * (see SessionSweepService.sweep's ordering) and finalizeIfGradingComplete re-queries the
 * pending count fresh rather than relying on a value computed earlier in the tick.
 * <p>
 * createdAt is @CreationTimestamp-managed (Hibernate sets it on insert regardless of what the
 * builder is given), so it's backdated here via a native update after persisting, mirroring how
 * an attempt that's genuinely been sitting PENDING for a while would look.
 */
@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
class SessionSweepServiceIntegrationTest {

    @Autowired
    private SessionSweepService sweepService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionQuestionRepository sessionQuestionRepository;
    @Autowired
    private AttemptRepository attemptRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void sweep_finalizesSessionInSameTickAsMarkingItsLastPendingAttemptFailed() {
        User user = userRepository.save(User.builder()
                .firstName("Test").lastName("User").webName("sweep-test-user")
                .email("sweep-test@example.com").passwordHash("hash").role(Role.USER).build());

        Topic topic = topicRepository.save(Topic.builder().topicName("Sweep Test Topic").description("d").build());

        Question question = questionRepository.save(Question.builder()
                .topic(topic).statement("stmt").questionType(QuestionType.OPEN_ENDED)
                .difficulty(Difficulty.EASY).timeLimit(120).score(10).build());

        Session session = sessionRepository.save(Session.builder()
                .user(user).sessionType(SessionType.FREE_MOCK).status(SessionStatus.AWAITING_GRADING).build());

        SessionQuestion sessionQuestion = sessionQuestionRepository.save(SessionQuestion.builder()
                .session(session).question(question).orderIndex(1).build());

        Attempt attempt = attemptRepository.save(Attempt.builder()
                .sessionQuestion(sessionQuestion).user(user).status(AttemptStatus.PENDING).build());
        entityManager.flush(); // the INSERT must actually hit the DB before a native UPDATE can find the row

        // Backdate createdAt well past the 30-minute staleness threshold (test yaml).
        // @CreationTimestamp overrides anything the builder sets at insert time, so this has to
        // happen as a separate update after the row already exists.
        int updated = entityManager.createNativeQuery("UPDATE attempts SET created_at = :cutoff WHERE id = :id")
                .setParameter("cutoff", LocalDateTime.now().minusHours(2))
                .setParameter("id", attempt.getId())
                .executeUpdate();
        assertThat(updated).isEqualTo(1); // fail fast here, not with a confusing assertion below, if this ever regresses
        entityManager.clear(); // forces the next reads below to hit the DB, not stale first-level cache

        sweepService.sweep();
        entityManager.flush(); // push any still-pending changes before clearing, not just discarding them
        entityManager.clear();

        Attempt reloadedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(reloadedAttempt.getStatus()).isEqualTo(AttemptStatus.FAILED);

        Session reloadedSession = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloadedSession.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(reloadedSession.getTotalScore()).isNotNull();
    }
}
