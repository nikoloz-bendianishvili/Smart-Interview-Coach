package interview_coach.services.core;

import interview_coach.entities.*;
import interview_coach.enums.GradingStatus;
import interview_coach.enums.QuestionType;
import interview_coach.exceptions.OptionNotFoundException;
import interview_coach.exceptions.SessionQuestionNotFoundException;
import interview_coach.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private final AttemptRepository attemptRepository;
    private final OptionRepository optionRepository;
    private final SessionQuestionRepository sessionQuestionRepository;
    private final CodeSubmissionRepository codeSubmissionRepository;
    private final VoiceAnswerRepository voiceAnswerRepository;
    private final AiGradingService aiGradingService;
    private final JudgeService judgeService;

    @Transactional
    public Attempt submitMCQAttempt(Long sessionQuestionId, User user, Integer selectedOption, int timeTakenSeconds) {
        SessionQuestion sq = sessionQuestionRepository.findById(sessionQuestionId)
                .orElseThrow(() -> new SessionQuestionNotFoundException("SessionQuestion not found"));
        Option option = optionRepository.findByQuestionId(sq.getQuestion().getId())
                .orElseThrow(() -> new OptionNotFoundException("No options found"));

        boolean isCorrect = selectedOption.equals(option.getCorrectOption());
        int score = isCorrect ? sq.getQuestion().getScore() : 0;

        Attempt attempt = Attempt.builder()
                .sessionQuestion(sq)
                .user(user)
                .selectedOption(selectedOption)
                .isCorrect(isCorrect)
                .score(score)
                .timeTakenSeconds(timeTakenSeconds)
                .wasSkipped(false)
                .build();

        return attemptRepository.save(attempt);
    }

    @Transactional
    public Attempt submitCodingAttempt(Long sessionQuestionId, User user, String sourceCode, int timeTakenSeconds) {
        SessionQuestion sq = sessionQuestionRepository.findById(sessionQuestionId)
                .orElseThrow(() -> new SessionQuestionNotFoundException("SessionQuestion not found"));

        Attempt attempt = Attempt.builder()
                .sessionQuestion(sq)
                .user(user)
                .timeTakenSeconds(timeTakenSeconds)
                .wasSkipped(false)
                .build();
        attemptRepository.save(attempt);

        CodeSubmission submission = CodeSubmission.builder()
                .attempt(attempt)
                .sourceCode(sourceCode)
                .status(GradingStatus.PENDING)
                .build();
        codeSubmissionRepository.save(submission);

        judgeService.gradeSubmission(submission.getId()); // async — runs Judge0, updates score once done

        return attempt;
    }

    @Transactional
    public Attempt submitOpenEndedAttempt(Long sessionQuestionId, User user, String answerText, int timeTakenSeconds) {
        SessionQuestion sq = sessionQuestionRepository.findById(sessionQuestionId)
                .orElseThrow(() -> new SessionQuestionNotFoundException("SessionQuestion not found"));

        Attempt attempt = Attempt.builder()
                .sessionQuestion(sq)
                .user(user)
                .textAnswer(answerText)
                .timeTakenSeconds(timeTakenSeconds)
                .wasSkipped(false)
                .build();
        attemptRepository.save(attempt);

        VoiceAnswer voiceAnswer = VoiceAnswer.builder()
                .attempt(attempt)
                .audioTranscript(answerText)
                .gradingStatus(GradingStatus.PENDING)
                .build();
        voiceAnswerRepository.save(voiceAnswer);

        aiGradingService.gradeAnswer(voiceAnswer.getId()); // async — calls AI API, updates score once done

        return attempt;
    }

    @Transactional
    public String giveUpAttempt(Long sessionQuestionId, User user) {
        SessionQuestion sq = sessionQuestionRepository.findById(sessionQuestionId)
                .orElseThrow(() -> new SessionQuestionNotFoundException("SessionQuestion not found"));

        Attempt attempt = Attempt.builder()
                .sessionQuestion(sq)
                .user(user)
                .score(0)
                .wasSkipped(true)
                .build();
        attemptRepository.save(attempt);

        Question question = sq.getQuestion();
        if (question.getQuestionType() == QuestionType.CODING) {
            return question.getExplanation() + "\n\n" + question.getCodingChallenge().getReferenceSolution();
        }
        return question.getExplanation();
    }

    public List<Attempt> getAttemptsBySession(Long sessionId) {
        return attemptRepository.findBySessionQuestion_Session_Id(sessionId);
    }

    public List<Attempt> getAttemptsByUser(Long userId) {
        return attemptRepository.findByUserId(userId);
    }

    public List<Attempt> getAttemptsByUserAndTopic(Long userId, Long topicId) {
        return attemptRepository.findByUserIdAndSessionQuestion_Question_Topic_Id(userId, topicId);
    }
}
