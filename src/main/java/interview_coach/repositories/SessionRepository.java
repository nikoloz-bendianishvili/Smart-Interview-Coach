package interview_coach.repositories;

import interview_coach.entities.Session;
import interview_coach.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByUserIdOrderByStartTimeDesc(Long userId);

    List<Session> findByUserIdAndStatus(Long userId, SessionStatus status);
}

