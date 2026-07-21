package interview_coach.repositories;

import interview_coach.entities.CodeSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodeSubmissionRepository extends JpaRepository<CodeSubmission, Long> {

    Optional<CodeSubmission> findByAttemptId(Long attemptId);
}

