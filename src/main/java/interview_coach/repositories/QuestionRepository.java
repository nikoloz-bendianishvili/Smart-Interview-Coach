package interview_coach.repositories;

import interview_coach.entities.Question;
import interview_coach.entities.Topic;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByTopicId(Long topicId);

    List<Question> findByDifficulty(Difficulty difficulty);

    List<Question> findByQuestionType(QuestionType questionType);

    List<Question> findByTopicIdAndQuestionType(Long topicId, QuestionType questionType);

    long countByTopicIdAndQuestionType(Long topicId, QuestionType questionType);

    @Query(value = "SELECT * FROM questions WHERE topic_id = :topicId AND question_type = :questionType " +
            "ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Question> findRandomByTopicAndType(
            @Param("topicId") Long topicId,
            @Param("questionType") String questionType,
            @Param("limit") int limit);

    @Query("SELECT AVG(q.timeLimit) FROM Question q WHERE q.questionType = :questionType")
    Double findAverageTimeLimitByType(@Param("questionType") QuestionType questionType);


    long countByQuestionType(QuestionType questionType);

    @Query(value = "SELECT * FROM questions WHERE question_type = :questionType ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Question> findRandomByType(@Param("questionType") String questionType, @Param("limit") int limit);
}
