package repositories;

import entities.CodingChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodingChallengeRepository extends JpaRepository<CodingChallenge, Long> {
}

