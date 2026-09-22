package interview_coach.repositories;

import interview_coach.entities.Attempt;
import interview_coach.enums.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    List<Attempt> findByUserId(Long userId);

    List<Attempt> findBySessionQuestion_Session_Id(Long sessionId);

    List<Attempt> findByUserIdAndSessionQuestion_Question_Topic_Id(Long userId, Long topicId);

    long countBySessionQuestion_Session_IdAndStatus(Long sessionId, AttemptStatus status);

    boolean existsBySessionQuestionId(Long sessionQuestionId);

    List<Attempt> findByStatusAndCreatedAtBefore(AttemptStatus status, LocalDateTime cutoff);
}

