package interview_coach.repositories;

import interview_coach.entities.Question;
import interview_coach.entities.Topic;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByTopicId(Long topicId);

    List<Question> findByDifficulty(Difficulty difficulty);

    List<Question> findByQuestionType(QuestionType questionType);

    List<Question> findByTopicIdAndQuestionType(Long topicId, QuestionType questionType);


}
