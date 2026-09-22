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
import interview_coach.exceptions.InvalidVoiceRecordingStateException;
import interview_coach.exceptions.OptionNotFoundException;
import interview_coach.exceptions.QuestionNotYetAvailableException;
import interview_coach.exceptions.QuestionTypeMismatchException;
import interview_coach.exceptions.SessionAccessDeniedException;
import interview_coach.exceptions.SessionAlreadyCompletedException;
import interview_coach.exceptions.SessionQuestionNotFoundException;
import interview_coach.repositories.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private final AttemptRepository attemptRepository;
    private final OptionRepository optionRepository;
    private final SessionQuestionRepository sessionQuestionRepository;
    private final CodeSubmissionRepository codeSubmissionRepository;
    private final VoiceAnswerRepository voiceAnswerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionService sessionService;
    private final Clock clock;

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

    /**
     * Starts (or restarts) server-side timing for a voice answer, ahead of the actual submit -
     * the row exists with no Attempt attached until submitOpenEndedAttempt links it. Calling
     * this again before stop/submit resets the take: startedAt is overwritten and any prior
     * endedAt is cleared, rather than accumulating separate takes.
     */
    @Transactional
    public void startVoiceRecording(Long sessionQuestionId, User user) {
        SessionQuestion sq = loadOwnedSessionQuestion(sessionQuestionId, user, QuestionType.OPEN_ENDED);

        VoiceAnswer voiceAnswer = voiceAnswerRepository.findBySessionQuestionId(sessionQuestionId)
                .orElseGet(() -> VoiceAnswer.builder()
                        .sessionQuestion(sq)
                        .gradingStatus(GradingStatus.PENDING)
                        .build());

        voiceAnswer.setStartedAt(LocalDateTime.now(clock));
        voiceAnswer.setEndedAt(null);
        voiceAnswerRepository.save(voiceAnswer);
    }

    /**
     * Stops server-side timing for a voice answer started via startVoiceRecording. Rejects a
     * stop with no matching start, rather than silently recording a nonsensical duration later.
     */
    @Transactional
    public void stopVoiceRecording(Long sessionQuestionId, User user) {
        loadOwnedSessionQuestion(sessionQuestionId, user, QuestionType.OPEN_ENDED);

        VoiceAnswer voiceAnswer = voiceAnswerRepository.findBySessionQuestionId(sessionQuestionId)
                .filter(va -> va.getStartedAt() != null)
                .orElseThrow(() -> new InvalidVoiceRecordingStateException(
                        "No active voice recording to stop - call start first."));

        voiceAnswer.setEndedAt(LocalDateTime.now(clock));
        voiceAnswerRepository.save(voiceAnswer);
    }

    /**
     * timeTakenSeconds from the caller is used as-is for a plain text open-ended answer (no
     * voice/start ever called for this question). If a voice recording exists, its
     * server-recorded startedAt/endedAt are used instead - the client's timeTakenSeconds is
     * discarded outright, never trusted, exactly per how the rest of this timing feature treats
     * client-reported time as advisory at best.
     */
    @Transactional
    public Attempt submitOpenEndedAttempt(Long sessionQuestionId, User user, String answerText, int timeTakenSeconds) {
        SessionQuestion sq = loadOwnedSessionQuestion(sessionQuestionId, user, QuestionType.OPEN_ENDED);

        Optional<VoiceAnswer> existingRecording = voiceAnswerRepository.findBySessionQuestionId(sessionQuestionId);

        int effectiveTimeTakenSeconds = timeTakenSeconds;
        if (existingRecording.isPresent()) {
            VoiceAnswer recording = existingRecording.get();
            if (recording.getEndedAt() == null) {
                throw new InvalidVoiceRecordingStateException(
                        "Voice recording was started but never stopped - call stop before submitting.");
            }
            effectiveTimeTakenSeconds = (int) Duration.between(recording.getStartedAt(), recording.getEndedAt()).getSeconds();
        }

        Attempt attempt = Attempt.builder()
                .sessionQuestion(sq)
                .user(user)
                .textAnswer(answerText)
                .timeTakenSeconds(effectiveTimeTakenSeconds)
                .status(AttemptStatus.PENDING)
                .build();
        attemptRepository.save(attempt);

        VoiceAnswer voiceAnswer = existingRecording.orElseGet(() -> VoiceAnswer.builder()
                .sessionQuestion(sq)
                .gradingStatus(GradingStatus.PENDING)
                .build());
        voiceAnswer.setAttempt(attempt);
        voiceAnswer.setAudioTranscript(answerText);
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

        // Check-on-access: a session whose deadline has passed gets closed right here, so a
        // submission arriving after expiry is rejected by the very next check rather than
        // silently accepted just because the scheduled sweep hasn't run yet.
        sessionService.closeIfExpired(sq.getSession());

        if (sq.getSession().getStatus() != SessionStatus.IN_PROGRESS) {
            throw new SessionAlreadyCompletedException("This session is no longer in progress.");
        }

        if (attemptRepository.existsBySessionQuestionId(sessionQuestionId)) {
            throw new AttemptAlreadyExistsException("This question has already been answered.");
        }

        // REAL_INTERVIEW enforces strict in-order answering (no skip-and-return, unlike
        // FREE_MOCK/CUSTOM_PRACTICE) - applies to give-up too (expectedType == null), not just
        // scored submissions, since jumping the queue by giving up ahead of time would defeat
        // the same guarantee.
        if (sq.getSession().getSessionType() == SessionType.REAL_INTERVIEW) {
            Long currentId = sessionQuestionRepository
                    .findFirstBySession_IdAndAttemptIsNullOrderByOrderIndexAsc(sq.getSession().getId())
                    .map(SessionQuestion::getId)
                    .orElse(null);
            if (!sessionQuestionId.equals(currentId)) {
                throw new QuestionNotYetAvailableException(
                        "Questions in a Real Interview session must be answered in order.");
            }
        }

        if (expectedType != null && sq.getQuestion().getQuestionType() != expectedType) {
            throw new QuestionTypeMismatchException(
                    "Expected a " + expectedType + " question but this question is " + sq.getQuestion().getQuestionType());
        }

        return sq;
    }
}