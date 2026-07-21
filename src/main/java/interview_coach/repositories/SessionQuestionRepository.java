package interview_coach.repositories;

import interview_coach.entities.SessionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, Long> {

    List<SessionQuestion> findBySessionIdOrderByOrderIndexAsc(Long sessionId);

}

