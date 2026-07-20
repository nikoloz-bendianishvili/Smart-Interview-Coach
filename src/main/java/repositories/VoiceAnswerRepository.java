package repositories;

import entities.VoiceAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceAnswerRepository extends JpaRepository<VoiceAnswer, Long> {
}

