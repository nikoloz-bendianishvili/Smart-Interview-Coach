package interview_coach.services.core;

import interview_coach.dto.QuestionUpdateDTO;
import interview_coach.entities.CodingChallenge;
import interview_coach.entities.Option;
import interview_coach.entities.Question;
import interview_coach.entities.TestCase;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import interview_coach.exceptions.OptionNotFoundException;
import interview_coach.exceptions.QuestionNotFoundException;
import interview_coach.repositories.CodingChallengeRepository;
import interview_coach.repositories.OptionRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final OptionRepository optionRepository;
    private final TestCaseRepository testCaseRepository;
    private final CodingChallengeRepository codingChallengeRepository;

    @Transactional
    public void createMCQQuestion(Question question, Option option) {
        if (option == null) {
            throw new IllegalArgumentException("MCQ question requires an option set.");
        }

        questionRepository.save(question);
        option.setQuestion(question);
        optionRepository.save(option);
    }

    @Transactional
    public void createOpenEndedQuestion(Question question) {
        questionRepository.save(question);
    }

    @Transactional
    public void createCodingQuestion(Question question, CodingChallenge codingChallenge, List<TestCase> testCases) {
        if (testCases == null || testCases.isEmpty()) {
            throw new IllegalArgumentException("Coding question requires at least one test case.");
        }

        questionRepository.save(question);
        codingChallenge.setQuestion(question);
        codingChallengeRepository.save(codingChallenge);

        for (TestCase tc : testCases) {
            tc.setCodingChallenge(codingChallenge);
            testCaseRepository.save(tc);
        }
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        questionRepository.deleteById(questionId);
    }

    @Transactional
    public void updateQuestion(Long questionId, QuestionUpdateDTO questionUpdateDTO) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException("Question not found with id: " + questionId));
        question.setTopic(questionUpdateDTO.topic());
        question.setStatement(questionUpdateDTO.statement());
        question.setDifficulty(questionUpdateDTO.difficulty());
        question.setTimeLimit(questionUpdateDTO.timeLimit());
        question.setExplanation(questionUpdateDTO.explanation());
        questionRepository.save(question);
    }

    public List<Question> getQuestionsByTopicId(Long topicId) {
        return questionRepository.findByTopicId(topicId);
    }

    public List<Question> getByDifficulty(Difficulty difficulty) {
        return questionRepository.findByDifficulty(difficulty);
    }

    public List<Question> getByQuestionType(QuestionType questionType) {
        return questionRepository.findByQuestionType(questionType);
    }

    public List<Question> getByTopicIdAndQuestionType(Long topicId, QuestionType questionType) {
        return questionRepository.findByTopicIdAndQuestionType(topicId, questionType);
    }

    public Option getOptionsByQuestionId(Long questionId) {
        return optionRepository.findByQuestionId(questionId)
                .orElseThrow(() -> new OptionNotFoundException("No options found for question id: " + questionId));
    }
}
