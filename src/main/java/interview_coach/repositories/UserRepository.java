package interview_coach.repositories;

import interview_coach.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByEmail(String email);

    User findByWebName(String webName);

    boolean existsByEmail(String email);
}
