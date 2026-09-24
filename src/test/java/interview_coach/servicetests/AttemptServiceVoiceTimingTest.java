package interview_coach.servicetests;

import interview_coach.entities.Attempt;
import interview_coach.entities.Question;
import interview_coach.entities.Session;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.User;
import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.exceptions.InvalidVoiceRecordingStateException;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.CodeSubmissionRepository;
import interview_coach.repositories.OptionRepository;
import interview_coach.repositories.SessionQuestionRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import interview_coach.services.core.AttemptService;
import interview_coach.services.core.SessionService;
import interview_coach.services.storage.AudioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifies the start/stop/submit voice-timing flow: server-recorded timestamps drive
 * timeTakenSeconds at submit, never the client's own value, whenever a recording exists; a
 * second start resets the take; and the sequencing violations (stop-without-start,
 * submit-without-stop) are rejected rather than silently producing a nonsensical duration.
 * Plain text open-ended answers (no start ever called) are unaffected - same client-trusted
 * behavior as before this feature existed.
 */
@ExtendWith(MockitoExtension.class)
class AttemptServiceVoiceTimingTest {

    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private OptionRepository optionRepository;
    @Mock
    private SessionQuestionRepository sessionQuestionRepository;
    @Mock
    private CodeSubmissionRepository codeSubmissionRepository;
    @Mock
    private VoiceAnswerRepository voiceAnswerRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SessionService sessionService;
    @Mock
    private AudioStorageService audioStorageService;
    @Mock
    private Clock clock;

    private AttemptService attemptService;
    private User owner;
    private SessionQuestion sq;

    /** In-memory stand-in for the one VoiceAnswer row this question can have, so a sequence of
     *  start/stop/submit calls within one test sees each other's writes like a real DB would. */
    private final AtomicReference<VoiceAnswer> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        attemptService = new AttemptService(attemptRepository, optionRepository, sessionQuestionRepository,
                codeSubmissionRepository, voiceAnswerRepository, eventPublisher, sessionService,
                audioStorageService, clock);

        owner = User.builder().id(1L).build();
        Session session = Session.builder().id(1L).user(owner).status(SessionStatus.IN_PROGRESS).build();
        Question question = Question.builder().id(1L).score(15).questionType(QuestionType.OPEN_ENDED).build();
        sq = SessionQuestion.builder().id(1L).session(session).question(question).orderIndex(1).build();

        when(sessionQuestionRepository.findById(1L)).thenReturn(Optional.of(sq));
        lenient().when(attemptRepository.existsBySessionQuestionId(1L)).thenReturn(false);

        when(voiceAnswerRepository.findBySessionQuestionId(1L)).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        lenient().when(voiceAnswerRepository.save(any(VoiceAnswer.class))).thenAnswer(inv -> {
            VoiceAnswer va = inv.getArgument(0);
            stored.set(va);
            return va;
        });
        lenient().when(attemptRepository.save(any(Attempt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubClockSequence(Instant... instants) {
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        Instant first = instants[0];
        Instant[] rest = new Instant[instants.length - 1];
        System.arraycopy(instants, 1, rest, 0, rest.length);
        when(clock.instant()).thenReturn(first, rest);
    }

    @Test
    void startThenStopThenSubmit_computesServerSideDurationIgnoringClientValue() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T00:05:00Z"); // 5 minutes later
        stubClockSequence(t1, t2);

        attemptService.startVoiceRecording(1L, owner);
        attemptService.stopVoiceRecording(1L, owner);

        // Client claims 999s - must be ignored in favor of the real (300s) server-measured gap.
        Attempt attempt = attemptService.submitOpenEndedAttempt(1L, owner, "my answer", 999);

        assertThat(attempt.getTimeTakenSeconds()).isEqualTo(300);
        assertThat(stored.get().getAttempt()).isEqualTo(attempt);
        assertThat(stored.get().getAudioTranscript()).isEqualTo("my answer");
    }

    @Test
    void secondStart_resetsStartedAtAndClearsAnyPriorEndedAt() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T00:05:00Z");
        Instant t3 = Instant.parse("2026-01-01T00:10:00Z"); // a fresh take, started over
        stubClockSequence(t1, t2, t3);

        attemptService.startVoiceRecording(1L, owner); // t1
        attemptService.stopVoiceRecording(1L, owner);  // t2
        attemptService.startVoiceRecording(1L, owner); // t3 - resets the take

        assertThat(stored.get().getStartedAt()).isEqualTo(java.time.LocalDateTime.ofInstant(t3, ZoneId.of("UTC")));
        assertThat(stored.get().getEndedAt()).isNull();
    }

    @Test
    void stop_withNoPriorStart_throwsInvalidVoiceRecordingState() {
        assertThatThrownBy(() -> attemptService.stopVoiceRecording(1L, owner))
                .isInstanceOf(InvalidVoiceRecordingStateException.class);
    }

    @Test
    void submit_startedButNeverStopped_throwsInvalidVoiceRecordingState() {
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));

        attemptService.startVoiceRecording(1L, owner);

        assertThatThrownBy(() -> attemptService.submitOpenEndedAttempt(1L, owner, "answer", 30))
                .isInstanceOf(InvalidVoiceRecordingStateException.class);
    }

    @Test
    void submit_withNoRecordingEverStarted_usesClientProvidedTimeTakenSecondsUnchanged() {
        // Plain text open-ended answer - voice/start was never called for this question.
        Attempt attempt = attemptService.submitOpenEndedAttempt(1L, owner, "typed answer", 42);

        assertThat(attempt.getTimeTakenSeconds()).isEqualTo(42);
    }
}
