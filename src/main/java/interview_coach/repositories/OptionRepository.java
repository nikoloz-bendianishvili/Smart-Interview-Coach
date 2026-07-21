package interview_coach.repositories;

import interview_coach.entities.Option;
import interview_coach.entities.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OptionRepository extends JpaRepository<Option, Long> {

    Optional<Option> findByQuestionId(Question questionId);
}

