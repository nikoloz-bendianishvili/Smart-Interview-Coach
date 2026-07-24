package interview_coach.repositorytests;

import interview_coach.entities.Option;
import interview_coach.entities.User;
import interview_coach.enums.Role;
import interview_coach.InterviewCoachApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import interview_coach.repositories.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InterviewCoachApplication.class)
@Transactional
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void repositoryLoads() {
        assertThat(userRepository).isNotNull();
    }

    @Test
    void saveAndFindByEmail() {
        User u = User.builder()
                .firstName("Alice")
                .lastName("Smith")
                .webName("alice")
                .email("alice@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .isBanned(false)
                .build();

        userRepository.save(u);

        User found = userRepository.findByEmail("alice@example.com");
        assertThat(found).isNotNull();
        assertThat(found.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void existsByWebNameReturnsTrueWhenTaken() {
        User u = User.builder()
                .firstName("Bob")
                .lastName("Jones")
                .webName("bobby")
                .email("bob@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .isBanned(false)
                .build();

        userRepository.save(u);

        boolean exists = userRepository.existsByWebName("bobby");
        boolean notExists = userRepository.existsByWebName("nonexistent");

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }
}



