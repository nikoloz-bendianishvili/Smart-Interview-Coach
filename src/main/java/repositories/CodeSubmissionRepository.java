package repositories;

import entities.CodeSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeSubmissionRepository extends JpaRepository<CodeSubmission, Long> {
}

