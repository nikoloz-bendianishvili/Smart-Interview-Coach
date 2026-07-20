package repositories;

import entities.Question;
import entities.Topic;
import enums.Difficulty;
import enums.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByTopicId(Topic topicId);

    List<Question> findByDifficulty(Difficulty difficulty);

    List<Question> findByQuestionType(QuestionType questionType);
}
