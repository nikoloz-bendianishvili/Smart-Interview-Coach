package interview_coach.repositories;

import interview_coach.entities.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByCodingChallengeId(Long challengeId);

    List<TestCase> findByCodingChallengeIdAndIsHidden(Long challengeId, boolean isHidden);
}

