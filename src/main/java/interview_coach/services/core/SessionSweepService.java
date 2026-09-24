package interview_coach.services.core;

import interview_coach.entities.Attempt;
import interview_coach.entities.Session;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.GradingStatus;
import interview_coach.enums.SessionStatus;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.SessionRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic backstop for the session-timing feature: closes sessions nobody is ever going to
 * touch again (the "user just left" case a lazy check-on-access can never catch), and covers
 * two failure modes the per-request/per-grader paths can't reliably resolve on their own:
 * <p>
 * 1. Orphaned AWAITING_GRADING sessions - the per-grader finalization hook (see
 * JudgeService/AiGradingService) is best-effort, not guaranteed: under MySQL's default
 * REPEATABLE_READ isolation, two graders finishing within the same window can each take a
 * snapshot before the other's commit and each see the other as still PENDING, so neither
 * finalizes even though the true state is "nothing pending." This sweep re-queries fresh on
 * every tick and finalizes anything that's actually done.
 * <p>
 * 2. Attempts stuck PENDING forever - @Async/@TransactionalEventListener work is in-memory
 * only; if the app restarts between an event being published and its handler running, nothing
 * ever retries it. Left alone, that attempt (and the session waiting on it) would never resolve.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSweepService {

    private final SessionRepository sessionRepository;
    private final AttemptRepository attemptRepository;
    private final CodeSubmissionRepository codeSubmissionRepository;
    private final VoiceAnswerRepository voiceAnswerRepository;
    private final SessionService sessionService;
    private final Clock clock;

    @Value("${app.grading.stale-pending-minutes}")
    private long stalePendingMinutes;

    @Scheduled(fixedDelayString = "${app.session.sweep-interval-ms}")
    @Transactional
    public void sweep() {
        // Order matters: staleness-marking runs first so a session that loses its last pending
        // attempt to that rule gets finalized in this same tick, not the next one.
        markStalePendingAttemptsFailed();
        closeExpiredInProgressSessions();
        finalizeOrphanedAwaitingGradingSessions();
    }

    private void markStalePendingAttemptsFailed() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(stalePendingMinutes);
        List<Attempt> stale = attemptRepository.findByStatusAndCreatedAtBefore(AttemptStatus.PENDING, cutoff);

        for (Attempt attempt : stale) {
            log.warn("Attempt {} has been PENDING for over {} minutes (createdAt={}) - marking FAILED. " +
                            "Likely an app restart mid-grading, since @Async grading work isn't retried across restarts.",
                    attempt.getId(), stalePendingMinutes, attempt.getCreatedAt());

            attempt.setStatus(AttemptStatus.FAILED);
            attemptRepository.save(attempt);

            codeSubmissionRepository.findByAttemptId(attempt.getId()).ifPresent(submission -> {
                submission.setStatus(GradingStatus.FAILED);
                codeSubmissionRepository.save(submission);
            });
            voiceAnswerRepository.findByAttemptId(attempt.getId()).ifPresent(voiceAnswer -> {
                voiceAnswer.setGradingStatus(GradingStatus.FAILED);
                voiceAnswerRepository.save(voiceAnswer);
            });
        }
    }

    private void closeExpiredInProgressSessions() {
        for (Session session : sessionRepository.findByStatus(SessionStatus.IN_PROGRESS)) {
            // closeIfExpired is a no-op if this particular session hasn't actually hit its
            // deadline+grace yet - reusing it here (rather than re-deriving the cutoff) keeps
            // exactly one definition of "expired" for every call site.
            sessionService.closeIfExpired(session);
        }
    }

    private void finalizeOrphanedAwaitingGradingSessions() {
        for (Session session : sessionRepository.findByStatus(SessionStatus.AWAITING_GRADING)) {
            sessionService.finalizeIfGradingComplete(session);
        }
    }
}
