package interview_coach.repositories;

import interview_coach.entities.CodingChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodingChallengeRepository extends JpaRepository<CodingChallenge, Long> {

    Optional<CodingChallenge> findByQuestionId(Long questionId);
}

