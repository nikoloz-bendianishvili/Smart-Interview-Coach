package interview_coach.repositorytests;

import interview_coach.entities.Session;
import interview_coach.entities.User;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.enums.Role;
import interview_coach.InterviewCoachApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.SessionRepository;
import interview_coach.repositories.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class SessionRepositoryTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void repositoryLoads() {
        assertThat(sessionRepository).isNotNull();
    }

    @Test
    void saveAndFindByUserAndStatus() {
        User u = User.builder().firstName("U").lastName("L").webName("u1").email("u1@example.com").passwordHash("p").role(Role.USER).isActive(true).build();
        userRepository.save(u);

        Session s = Session.builder().user(u).sessionType(SessionType.FREE_MOCK).status(SessionStatus.IN_PROGRESS).build();
        sessionRepository.save(s);

        assertThat(sessionRepository.findByUserIdOrderByStartTimeDesc(u.getId())).isNotEmpty();
        assertThat(sessionRepository.findByUserIdAndStatus(u.getId(), SessionStatus.IN_PROGRESS)).isNotEmpty();
    }
}



