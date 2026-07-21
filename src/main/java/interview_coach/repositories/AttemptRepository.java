package interview_coach.repositories;

import interview_coach.entities.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    List<Attempt> findByUserId(Long userId);

    List<Attempt> findByUserIdAndSessionQuestion_Question_Topic_Id(Long userId, Long topicId);
}

