package interview_coach.servicetests;

import interview_coach.entities.Attempt;
import interview_coach.entities.CodeSubmission;
import interview_coach.entities.Session;
import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.GradingStatus;
import interview_coach.enums.SessionStatus;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.SessionRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import interview_coach.services.core.SessionService;
import interview_coach.services.core.SessionSweepService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies SessionSweepService's own orchestration in isolation: which sub-operations it runs,
 * against which candidate rows, and that stale-attempt-marking runs before the
 * AWAITING_GRADING finalization pass (so a session freed up by staleness gets a chance to
 * finalize in the same tick). The actual cross-repository "does this really finalize in one
 * tick" behavior is covered separately in SessionSweepServiceIntegrationTest against a real
 * Spring context, since that emerges from real interaction between two repositories that
 * mocking here would just assert past.
 */
@ExtendWith(MockitoExtension.class)
class SessionSweepServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.systemDefault());

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private CodeSubmissionRepository codeSubmissionRepository;
    @Mock
    private VoiceAnswerRepository voiceAnswerRepository;
    @Mock
    private SessionService sessionService;

    private SessionSweepService sweepService;

    @BeforeEach
    void setUp() {
        sweepService = new SessionSweepService(sessionRepository, attemptRepository, codeSubmissionRepository,
                voiceAnswerRepository, sessionService, FIXED_CLOCK);
        ReflectionTestUtils.setField(sweepService, "stalePendingMinutes", 30L);
    }

    @Test
    void sweep_marksStalePendingAttemptsFailedAndTheirChildRecordsToo() {
        Attempt staleCoding = Attempt.builder().id(1L).status(AttemptStatus.PENDING).build();
        when(attemptRepository.findByStatusAndCreatedAtBefore(eq(AttemptStatus.PENDING), any())).thenReturn(List.of(staleCoding));

        CodeSubmission submission = CodeSubmission.builder().id(10L).status(GradingStatus.PENDING).build();
        when(codeSubmissionRepository.findByAttemptId(1L)).thenReturn(Optional.of(submission));
        when(voiceAnswerRepository.findByAttemptId(1L)).thenReturn(Optional.empty());

        when(sessionRepository.findByStatus(SessionStatus.IN_PROGRESS)).thenReturn(List.of());
        when(sessionRepository.findByStatus(SessionStatus.AWAITING_GRADING)).thenReturn(List.of());

        sweepService.sweep();

        assertThat(staleCoding.getStatus()).isEqualTo(AttemptStatus.FAILED);
        verify(attemptRepository).save(staleCoding);
        assertThat(submission.getStatus()).isEqualTo(GradingStatus.FAILED);
        verify(codeSubmissionRepository).save(submission);
        verify(voiceAnswerRepository, never()).save(any());
    }

    @Test
    void sweep_marksStaleVoiceAnswerAttemptsFailedToo() {
        Attempt staleOpenEnded = Attempt.builder().id(2L).status(AttemptStatus.PENDING).build();
        when(attemptRepository.findByStatusAndCreatedAtBefore(eq(AttemptStatus.PENDING), any())).thenReturn(List.of(staleOpenEnded));
        when(codeSubmissionRepository.findByAttemptId(2L)).thenReturn(Optional.empty());

        VoiceAnswer voiceAnswer = VoiceAnswer.builder().id(20L).gradingStatus(GradingStatus.PENDING).build();
        when(voiceAnswerRepository.findByAttemptId(2L)).thenReturn(Optional.of(voiceAnswer));

        when(sessionRepository.findByStatus(SessionStatus.IN_PROGRESS)).thenReturn(List.of());
        when(sessionRepository.findByStatus(SessionStatus.AWAITING_GRADING)).thenReturn(List.of());

        sweepService.sweep();

        assertThat(voiceAnswer.getGradingStatus()).isEqualTo(GradingStatus.FAILED);
        verify(voiceAnswerRepository).save(voiceAnswer);
    }

    @Test
    void sweep_runsCloseIfExpiredForEveryInProgressSession() {
        when(attemptRepository.findByStatusAndCreatedAtBefore(eq(AttemptStatus.PENDING), any())).thenReturn(List.of());
        Session s1 = Session.builder().id(1L).status(SessionStatus.IN_PROGRESS).build();
        Session s2 = Session.builder().id(2L).status(SessionStatus.IN_PROGRESS).build();
        when(sessionRepository.findByStatus(SessionStatus.IN_PROGRESS)).thenReturn(List.of(s1, s2));
        when(sessionRepository.findByStatus(SessionStatus.AWAITING_GRADING)).thenReturn(List.of());

        sweepService.sweep();

        verify(sessionService).closeIfExpired(s1);
        verify(sessionService).closeIfExpired(s2);
    }

    @Test
    void sweep_runsFinalizeIfGradingCompleteForEveryAwaitingGradingSession() {
        when(attemptRepository.findByStatusAndCreatedAtBefore(eq(AttemptStatus.PENDING), any())).thenReturn(List.of());
        when(sessionRepository.findByStatus(SessionStatus.IN_PROGRESS)).thenReturn(List.of());
        Session orphaned = Session.builder().id(3L).status(SessionStatus.AWAITING_GRADING).build();
        when(sessionRepository.findByStatus(SessionStatus.AWAITING_GRADING)).thenReturn(List.of(orphaned));

        sweepService.sweep();

        verify(sessionService).finalizeIfGradingComplete(orphaned);
    }

    @Test
    void sweep_marksStaleAttemptsBeforeCheckingAwaitingGradingSessions() {
        when(attemptRepository.findByStatusAndCreatedAtBefore(eq(AttemptStatus.PENDING), any())).thenReturn(List.of());
        when(sessionRepository.findByStatus(SessionStatus.IN_PROGRESS)).thenReturn(List.of());
        when(sessionRepository.findByStatus(SessionStatus.AWAITING_GRADING)).thenReturn(List.of());

        sweepService.sweep();

        InOrder inOrder = inOrder(attemptRepository, sessionRepository);
        inOrder.verify(attemptRepository).findByStatusAndCreatedAtBefore(eq(AttemptStatus.PENDING), any());
        inOrder.verify(sessionRepository).findByStatus(SessionStatus.AWAITING_GRADING);
    }
}
