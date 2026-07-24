package interview_coach.repositories;

import interview_coach.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByWebName(String webName);

    boolean existsByEmail(String email);

    boolean existsByWebName(String webName);
}
