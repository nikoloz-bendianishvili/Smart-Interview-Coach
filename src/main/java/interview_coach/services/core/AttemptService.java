package interview_coach.services.core;

import interview_coach.entities.*;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.GradingStatus;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.enums.SessionType;
import interview_coach.events.CodeSubmissionCreatedEvent;
import interview_coach.events.VoiceAnswerCreatedEvent;
import interview_coach.exceptions.AttemptAlreadyExistsException;
import interview_coach.exceptions.AttemptNotFoundException;
import interview_coach.exceptions.OptionNotFoundException;
import interview_coach.exceptions.QuestionTypeMismatchException;
import interview_coach.exceptions.SessionAccessDeniedException;
import interview_coach.exceptions.SessionAlreadyCompletedException;
import interview_coach.exceptions.SessionQuestionNotFoundException;
import interview_coach.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Result of {@link #giveUpAttempt}: the recorded (SKIPPED) attempt plus the
     * explanation text to show the user.
     */
    public record GiveUpResult(Attempt attempt, String explanation) {
    }

    @Transactional
    public Attempt submitMCQAttempt(Long sessionQuestionId, User user, Integer selectedOption, int timeTakenSeconds) {
        SessionQuestion sq = loadOwnedSessionQuestion(sessionQuestionId, user, QuestionType.MCQ);
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
                .status(AttemptStatus.GRADED) // MCQ grades instantly, no async pipeline
                .build();

        return attemptRepository.save(attempt);
    }

    @Transactional
    public Attempt submitCodingAttempt(Long sessionQuestionId, User user, String sourceCode, int timeTakenSeconds) {
        SessionQuestion sq = loadOwnedSessionQuestion(sessionQuestionId, user, QuestionType.CODING);

        Attempt attempt = Attempt.builder()
                .sessionQuestion(sq)
                .user(user)
                .timeTakenSeconds(timeTakenSeconds)
                .status(AttemptStatus.PENDING)
                .build();
        attemptRepository.save(attempt);

        CodeSubmission submission = CodeSubmission.builder()
                .attempt(attempt)
                .sourceCode(sourceCode)
                .status(GradingStatus.PENDING)
                .build();
        codeSubmissionRepository.save(submission);

        eventPublisher.publishEvent(new CodeSubmissionCreatedEvent(submission.getId()));

        return attempt;
    }

    @Transactional
    public Attempt submitOpenEndedAttempt(Long sessionQuestionId, User user, String answerText, int timeTakenSeconds) {
        SessionQuestion sq = loadOwnedSessionQuestion(sessionQuestionId, user, QuestionType.OPEN_ENDED);

        Attempt attempt = Attempt.builder()
                .sessionQuestion(sq)
                .user(user)
                .textAnswer(answerText)
                .timeTakenSeconds(timeTakenSeconds)
                .status(AttemptStatus.PENDING)
                .build();
        attemptRepository.save(attempt);

        VoiceAnswer voiceAnswer = VoiceAnswer.builder()
                .attempt(attempt)
                .audioTranscript(answerText)
                .gradingStatus(GradingStatus.PENDING)
                .build();
        voiceAnswerRepository.save(voiceAnswer);

        eventPublisher.publishEvent(new VoiceAnswerCreatedEvent(voiceAnswer.getId()));

        return attempt;
    }

    @Transactional
    public GiveUpResult giveUpAttempt(Long sessionQuestionId, User user) {
        SessionQuestion sq = loadOwnedSessionQuestion(sessionQuestionId, user, null);

        Attempt attempt = Attempt.builder()
                .sessionQuestion(sq)
                .user(user)
                .score(0)
                .status(AttemptStatus.SKIPPED)
                .build();
        attemptRepository.save(attempt);

        Question question = sq.getQuestion();
        SessionType sessionType = sq.getSession().getSessionType();

        String explanation;
        if (sessionType == SessionType.CUSTOM_PRACTICE) {
            if (question.getQuestionType() == QuestionType.CODING) {
                explanation = question.getExplanation() + "\n\n" + question.getCodingChallenge().getReferenceSolution();
            } else {
                explanation = question.getExplanation();
            }
        } else {
            explanation = "Answer recorded. You'll see the explanation when the session ends.";
        }

        return new GiveUpResult(attempt, explanation);
    }

    /**
     * Loads the attempt behind an id, throwing 404 if it doesn't exist and 403 if it
     * doesn't belong to {@code user}.
     */
    public Attempt getAttemptForUser(Long attemptId, User user) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AttemptNotFoundException("Attempt not found"));

        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new SessionAccessDeniedException("This attempt does not belong to the current user.");
        }

        return attempt;
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

    /**
     * Loads a SessionQuestion and validates that the caller may submit an attempt
     * against it: it exists, belongs to {@code user}, its session is still
     * IN_PROGRESS, it hasn't already been answered, and (unless {@code expectedType}
     * is null, as for give-up) its question is of the expected type.
     */
    private SessionQuestion loadOwnedSessionQuestion(Long sessionQuestionId, User user, QuestionType expectedType) {
        SessionQuestion sq = sessionQuestionRepository.findById(sessionQuestionId)
                .orElseThrow(() -> new SessionQuestionNotFoundException("SessionQuestion not found"));

        if (!sq.getSession().getUser().getId().equals(user.getId())) {
            throw new SessionAccessDeniedException("This session does not belong to the current user.");
        }

        if (sq.getSession().getStatus() != SessionStatus.IN_PROGRESS) {
            throw new SessionAlreadyCompletedException("This session is no longer in progress.");
        }

        if (attemptRepository.existsBySessionQuestionId(sessionQuestionId)) {
            throw new AttemptAlreadyExistsException("This question has already been answered.");
        }

        if (expectedType != null && sq.getQuestion().getQuestionType() != expectedType) {
            throw new QuestionTypeMismatchException(
                    "Expected a " + expectedType + " question but this question is " + sq.getQuestion().getQuestionType());
        }

        return sq;
    }
}