package repositories;

import entities.SessionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, Long> {
}

