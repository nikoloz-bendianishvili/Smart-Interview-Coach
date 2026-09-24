package interview_coach.repositories;

import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.GradingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoiceAnswerRepository extends JpaRepository<VoiceAnswer, Long> {

    Optional<VoiceAnswer> findByAttemptId(Long attemptId);

    List<VoiceAnswer> findByGradingStatus(GradingStatus status);

    /**
     * Looks up a recording by the question it belongs to, valid both before submit (attempt is
     * still null, keyed only by sessionQuestion) and after (attempt now set too).
     */
    Optional<VoiceAnswer> findBySessionQuestionId(Long sessionQuestionId);
}

