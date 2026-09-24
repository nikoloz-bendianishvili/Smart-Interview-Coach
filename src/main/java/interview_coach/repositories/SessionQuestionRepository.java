package interview_coach.repositories;

import interview_coach.entities.SessionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, Long> {

    List<SessionQuestion> findBySessionIdOrderByOrderIndexAsc(Long sessionId);

    /**
     * The "current" question for strict-order session types (REAL_INTERVIEW): the lowest
     * orderIndex SessionQuestion in this session that doesn't have an Attempt yet. Empty once
     * every question has been answered or given up.
     */
    Optional<SessionQuestion> findFirstBySession_IdAndAttemptIsNullOrderByOrderIndexAsc(Long sessionId);

}

