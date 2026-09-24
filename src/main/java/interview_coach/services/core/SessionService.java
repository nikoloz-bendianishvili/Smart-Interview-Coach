package interview_coach.services.core;

import interview_coach.dto.SessionDTO;
import interview_coach.entities.*;
import interview_coach.enums.*;
import interview_coach.exceptions.InsufficientQuestionsException;
import interview_coach.exceptions.InvalidSessionRequestException;
import interview_coach.exceptions.SessionAccessDeniedException;
import interview_coach.exceptions.SessionAlreadyCompletedException;
import interview_coach.exceptions.SessionNotFoundException;
import interview_coach.exceptions.SessionQuestionNotFoundException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.QuestionRepository;
import interview_coach.repositories.SessionQuestionRepository;
import interview_coach.repositories.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final SessionQuestionRepository sessionQuestionRepository;
    private final Clock clock;

    @Value("${app.session.time-limit-multiplier.real-interview}")
    private double realInterviewMultiplier;

    @Value("${app.session.time-limit-multiplier.free-mock}")
    private double freeMockMultiplier;

    @Value("${app.session.time-limit-multiplier.custom-practice}")
    private double customPracticeMultiplier;

    @Value("${app.session.grace-period-seconds}")
    private long gracePeriodSeconds;

    private static final double OPEN_ENDED_RATIO = 0.75;

    public Session getSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found with id: " + sessionId));
    }

    /**
     * Ownership-checked read used by the GET status endpoint. Also runs closeIfExpired, so a
     * client polling a session nobody has touched since it expired sees the up-to-date state
     * rather than a stale IN_PROGRESS - the same "check on access" every mutating route already
     * gets, extended to this read-only one too.
     */
    @Transactional
    public Session getSessionForUser(Long sessionId, User user) {
        Session session = getSessionById(sessionId);
        if (!session.getUser().getId().equals(user.getId())) {
            throw new SessionAccessDeniedException("This session does not belong to the current user.");
        }
        closeIfExpired(session);
        return session;
    }

    /**
     * The current question for the ordered-session-type UI: the lowest orderIndex
     * SessionQuestion in this session that hasn't been attempted (answered or given up) yet.
     * Well-defined for any session type, but specifically what REAL_INTERVIEW's client needs
     * since startSession only ever hands it question 1 up front - FREE_MOCK/CUSTOM_PRACTICE
     * clients already got every question at start and don't need to call this.
     */
    public SessionQuestion getCurrentQuestion(Long sessionId, User user) {
        Session session = getSessionById(sessionId);
        if (!session.getUser().getId().equals(user.getId())) {
            throw new SessionAccessDeniedException("This session does not belong to the current user.");
        }

        return sessionQuestionRepository.findFirstBySession_IdAndAttemptIsNullOrderByOrderIndexAsc(sessionId)
                .orElseThrow(() -> new SessionQuestionNotFoundException(
                        "No current question - every question in this session has already been answered or given up."));
    }

    @Transactional
    public Session startSession(SessionDTO sessionDTO) {
        Session session = Session.builder()
                .user(sessionDTO.user())
                .topic(sessionDTO.topic())
                .sessionType(sessionDTO.sessionType())
                .questionType(sessionDTO.questionType())
                .interactionMode(sessionDTO.interactionMode())
                .status(SessionStatus.IN_PROGRESS)
                .numOfQuestions(sessionDTO.numOfQuestions())
                .timeLimitMinutes(sessionDTO.timeLimitInMinutes())
                .build();
        sessionRepository.save(session);

        List<Question> questions = selectQuestionsForSession(session);

        List<SessionQuestion> sessionQuestions = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            sessionQuestions.add(SessionQuestion.builder()
                    .session(session)
                    .question(questions.get(i))
                    .orderIndex(i + 1)
                    .build());
        }
        sessionQuestionRepository.saveAll(sessionQuestions);

        session.setSessionDeadline(computeDeadline(session, questions));
        sessionRepository.save(session);

        return session;
    }

    /**
     * Explicit, user-initiated completion. Never blocks on grading still being PENDING - if
     * anything's still in flight, the session moves to AWAITING_GRADING (endedByTimeout=false)
     * rather than being rejected; JudgeService/AiGradingService (Phase 4) finalize it to
     * COMPLETED once every attempt resolves. Also runs the same expiry check as any other
     * access, so a call arriving after the deadline behaves identically to the lazy/scheduled
     * paths (endedByTimeout=true) rather than as a manual completion.
     */
    @Transactional
    public Session completeSession(Long sessionId, User user) {
        Session session = getSessionById(sessionId);

        if (!session.getUser().getId().equals(user.getId())) {
            throw new SessionAccessDeniedException("This session does not belong to the current user.");
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS && session.getStatus() != SessionStatus.AWAITING_GRADING) {
            throw new SessionAlreadyCompletedException("This session is not in progress.");
        }

        if (closeIfExpired(session)) {
            return session;
        }

        finalizeOrAwaitGrading(session, false);
        return sessionRepository.save(session);
    }

    /**
     * Checks sessionDeadline + grace against the injected Clock and, if passed, closes the
     * session (endedByTimeout=true) via the same finalize-or-await-grading logic used by manual
     * completion. Called from every access point - submit/give-up, completeSession, the
     * session-status GET endpoint, and the scheduled sweep - so there is exactly one definition
     * of "expired", not a separate one per call site.
     *
     * @return true if the session was (or already was) closed by this check, false if it's
     * still genuinely IN_PROGRESS and the caller should proceed with its own logic.
     */
    public boolean closeIfExpired(Session session) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            // COMPLETED is already terminal; AWAITING_GRADING isn't a deadline concern at all
            // (nothing left to expire) - re-checking whether its grading has since finished is
            // completeSession's/the sweep's job, not this one's.
            return session.getStatus() == SessionStatus.COMPLETED;
        }
        if (session.getSessionDeadline() == null) {
            return false; // shouldn't happen post-Phase-1, but never treat "no deadline" as expired
        }

        LocalDateTime effectiveDeadline = session.getSessionDeadline().plusSeconds(gracePeriodSeconds);
        if (LocalDateTime.now(clock).isBefore(effectiveDeadline)) {
            return false;
        }

        finalizeOrAwaitGrading(session, true);
        sessionRepository.save(session);
        return true;
    }

    /**
     * Re-checks an AWAITING_GRADING session and finalizes it to COMPLETED if every attempt has
     * since resolved. Used by JudgeService/AiGradingService's post-grading hook and by the
     * scheduled sweep's orphan-finalization pass - both need to flip a session that's already
     * past IN_PROGRESS once nothing is left PENDING, without re-running completeSession's
     * ownership check (there's no User available in either of those call sites - a background
     * job and an async grader aren't acting on behalf of a request).
     */
    @Transactional
    public void finalizeIfGradingComplete(Session session) {
        if (session.getStatus() != SessionStatus.AWAITING_GRADING) {
            return;
        }
        long pendingCount = attemptRepository.countBySessionQuestion_Session_IdAndStatus(session.getId(), AttemptStatus.PENDING);
        if (pendingCount == 0) {
            // Compute before mutating anything: calculateTotalScore's own query can trigger
            // Hibernate's auto-flush-before-query if this entity is already dirty, which would
            // persist a half-updated row (status changed, score not yet) - a stray intermediate
            // flush like that is normally invisible (the transaction's final commit-flush tops
            // it up), but there's no reason to create the window at all.
            int totalScore = calculateTotalScore(session);
            session.setStatus(SessionStatus.COMPLETED);
            session.setTotalScore(totalScore);
            sessionRepository.save(session);
        }
    }

    /**
     * Shared terminal-state transition for both manual completion and expiry: COMPLETED if
     * nothing's still being graded, AWAITING_GRADING otherwise. Does not save - callers save
     * once they're done setting any other fields (e.g. endedByTimeout is already set by the
     * caller before/after this, depending on the path).
     */
    private void finalizeOrAwaitGrading(Session session, boolean dueToTimeout) {
        // Both queried before any field is mutated - same reasoning as finalizeIfGradingComplete.
        long pendingCount = attemptRepository.countBySessionQuestion_Session_IdAndStatus(session.getId(), AttemptStatus.PENDING);
        int totalScore = calculateTotalScore(session);

        session.setEndedByTimeout(dueToTimeout);
        session.setEndTime(LocalDateTime.now(clock));
        session.setStatus(pendingCount > 0 ? SessionStatus.AWAITING_GRADING : SessionStatus.COMPLETED);
        session.setTotalScore(totalScore);
    }

    public List<Session> getSessionsByUserDesc(Long userId) {
        return sessionRepository.findByUserIdOrderByStartTimeDesc(userId);
    }

    public List<Session> getSessionsByUserAndStatus(Long userId, SessionStatus status) {
        return sessionRepository.findByUserIdAndStatus(userId, status);
    }


    private List<Question> selectQuestionsForSession(Session session) {
        return switch (session.getSessionType()) {
            case CUSTOM_PRACTICE -> selectQuestionsForCustomPractice(
                    session.getTopic().getId(),
                    session.getQuestionType(),
                    session.getNumOfQuestions()
            );

            case REAL_INTERVIEW -> {
                validateRealInterviewInput(session);
                yield selectMixedQuestions(calculateQuestionCountFromTime(session.getTimeLimitMinutes()));
            }

            case FREE_MOCK -> {
                validateFreeMockInput(session);
                int total = session.getNumOfQuestions() != null
                        ? session.getNumOfQuestions()
                        : calculateQuestionCountFromTime(session.getTimeLimitMinutes());
                yield selectMixedQuestions(total);
            }
        };
    }

    private void validateRealInterviewInput(Session session) {
        if (session.getTimeLimitMinutes() == null) {
            throw new InvalidSessionRequestException("Real Interview requires timeLimitMinutes.");
        }
    }

    private List<Question> selectQuestionsForCustomPractice(Long topicId, QuestionType questionType, int numOfQuestions) {
        long available = questionRepository.countByTopicIdAndQuestionTypeAndActiveTrue(topicId, questionType);

        if (available < numOfQuestions) {
            throw new InsufficientQuestionsException(
                    "Only " + available + " questions available for this topic and type, but " + numOfQuestions + " were requested."
            );
        }

        return questionRepository.findRandomByTopicAndType(topicId, questionType.name(), numOfQuestions);
    }

    private void validateFreeMockInput(Session session) {
        boolean hasCount = session.getNumOfQuestions() != null;
        boolean hasTimeLimit = session.getTimeLimitMinutes() != null;

        if (hasCount == hasTimeLimit) {
            throw new InvalidSessionRequestException("Free Mock requires exactly one of numOfQuestions or timeLimitMinutes.");
        }
    }

    /**
     * Deadline = now + (multiplier x basis), where basis is the chosen time for REAL_INTERVIEW
     * and FREE_MOCK-by-time, or the selected questions' summed timeLimit (seconds) for
     * CUSTOM_PRACTICE and FREE_MOCK-by-count. Grace is deliberately NOT added here - it's only
     * ever applied at enforcement time (closeIfExpired), so the stored deadline always reflects
     * the "real" agreed time, and grace stays a single enforcement-side concept.
     */
    private LocalDateTime computeDeadline(Session session, List<Question> selectedQuestions) {
        LocalDateTime now = LocalDateTime.now(clock);

        return switch (session.getSessionType()) {
            case REAL_INTERVIEW ->
                    now.plusSeconds(Math.round(session.getTimeLimitMinutes() * 60.0 * realInterviewMultiplier));

            case FREE_MOCK -> {
                if (session.getTimeLimitMinutes() != null) {
                    yield now.plusSeconds(Math.round(session.getTimeLimitMinutes() * 60.0 * freeMockMultiplier));
                }
                int summedSeconds = selectedQuestions.stream().mapToInt(Question::getTimeLimit).sum();
                yield now.plusSeconds(Math.round(summedSeconds * freeMockMultiplier));
            }

            case CUSTOM_PRACTICE -> {
                int summedSeconds = selectedQuestions.stream().mapToInt(Question::getTimeLimit).sum();
                yield now.plusSeconds(Math.round(summedSeconds * customPracticeMultiplier));
            }
        };
    }

    private List<Question> selectMixedQuestions(int totalQuestions) {
        int openEndedCount = (int) Math.round(totalQuestions * OPEN_ENDED_RATIO);
        int codingCount = totalQuestions - openEndedCount;

        long availableCoding = questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.CODING);
        long availableOpenEnded = questionRepository.countByQuestionTypeAndActiveTrue(QuestionType.OPEN_ENDED);

        if (availableCoding < codingCount || availableOpenEnded < openEndedCount) {
            throw new InsufficientQuestionsException(
                    "Not enough questions available. Requested " + codingCount + " coding (have " + availableCoding +
                            ") and " + openEndedCount + " open-ended (have " + availableOpenEnded + ")."
            );
        }

        List<Question> questions = new ArrayList<>();
        questions.addAll(questionRepository.findRandomByType(QuestionType.CODING.name(), codingCount));
        questions.addAll(questionRepository.findRandomByType(QuestionType.OPEN_ENDED.name(), openEndedCount));
        Collections.shuffle(questions);
        return questions;
    }


    private int calculateQuestionCountFromTime(int timeLimitMinutes) {
        Double avgCodingSeconds = questionRepository.findAverageTimeLimitByType(QuestionType.CODING);
        Double avgOpenEndedSeconds = questionRepository.findAverageTimeLimitByType(QuestionType.OPEN_ENDED);

        if (avgCodingSeconds == null || avgOpenEndedSeconds == null) {
            throw new InsufficientQuestionsException("Not enough questions in the database to estimate session length.");
        }

        double avgSecondsPerQuestion =
                (OPEN_ENDED_RATIO * avgOpenEndedSeconds) +
                        ((1 - OPEN_ENDED_RATIO) * avgCodingSeconds);

        double timeLimitSeconds = timeLimitMinutes * 60.0;
        return Math.max(1, (int) Math.round(timeLimitSeconds / avgSecondsPerQuestion));
    }


    private int calculateTotalScore(Session session) {
        List<Attempt> attempts = attemptRepository.findBySessionQuestion_Session_Id(session.getId());

        return attempts.stream()
                .filter(attempt -> attempt.getStatus() != AttemptStatus.SKIPPED)
                .mapToInt(attempt -> attempt.getScore() != null ? attempt.getScore() : 0)
                .sum();
    }

    public List<SessionQuestion> getSessionQuestions(Long sessionId) {
        return sessionQuestionRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
    }

}
