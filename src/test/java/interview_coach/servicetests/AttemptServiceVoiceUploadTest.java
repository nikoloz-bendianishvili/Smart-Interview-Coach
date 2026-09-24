package interview_coach.servicetests;

import interview_coach.entities.Attempt;
import interview_coach.entities.Question;
import interview_coach.entities.Session;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.User;
import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.events.VoiceAudioSubmittedEvent;
import interview_coach.exceptions.AttemptAlreadyExistsException;
import interview_coach.exceptions.InvalidAudioUploadException;
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
import org.springframework.mock.web.MockMultipartFile;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers AttemptService.submitVoiceAttempt - the audio-upload counterpart to
 * submitOpenEndedAttempt's typed-text path. Storage and transcription are decoupled: this only
 * verifies the recording-state validation, the implicit-stop convenience, the duration
 * computed from server timestamps, and that a VoiceAudioSubmittedEvent (not
 * VoiceAnswerCreatedEvent - there's no transcript yet) is published.
 */
@ExtendWith(MockitoExtension.class)
class AttemptServiceVoiceUploadTest {

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

        lenient().when(voiceAnswerRepository.findBySessionQuestionId(1L)).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        lenient().when(voiceAnswerRepository.save(any(VoiceAnswer.class))).thenAnswer(inv -> {
            VoiceAnswer va = inv.getArgument(0);
            stored.set(va);
            return va;
        });
        lenient().when(attemptRepository.save(any(Attempt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MockMultipartFile audioFile() {
        return new MockMultipartFile("audio", "answer.webm", "audio/webm", "fake-audio-bytes".getBytes());
    }

    @Test
    void submit_afterStartAndStop_computesDurationStoresAudioAndPublishesAudioSubmittedEvent() {
        VoiceAnswer recording = VoiceAnswer.builder().sessionQuestion(sq)
                .startedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .endedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 5, 0))
                .build();
        stored.set(recording);
        when(audioStorageService.store(anyLong(), any())).thenReturn("sq-1-abc.webm");

        Attempt attempt = attemptService.submitVoiceAttempt(1L, owner, audioFile());

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.PENDING);
        assertThat(attempt.getTimeTakenSeconds()).isEqualTo(300);
        assertThat(attempt.getTextAnswer()).isNull(); // no transcript yet - TranscriptionService fills it later
        assertThat(stored.get().getAudioFileUrl()).isEqualTo("sq-1-abc.webm");
        assertThat(stored.get().getAttempt()).isEqualTo(attempt);

        verify(eventPublisher).publishEvent(any(VoiceAudioSubmittedEvent.class));
    }

    @Test
    void submit_startedButNeverStopped_implicitlyStopsUsingClockThenSucceeds() {
        VoiceAnswer recording = VoiceAnswer.builder().sessionQuestion(sq)
                .startedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .build(); // endedAt never set - stop was skipped
        stored.set(recording);

        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:02:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        when(audioStorageService.store(anyLong(), any())).thenReturn("sq-1-xyz.webm");

        Attempt attempt = attemptService.submitVoiceAttempt(1L, owner, audioFile());

        assertThat(stored.get().getEndedAt()).isNotNull();
        assertThat(attempt.getTimeTakenSeconds()).isEqualTo(120);
    }

    @Test
    void submit_withNoRecordingEverStarted_throwsInvalidVoiceRecordingState() {
        assertThatThrownBy(() -> attemptService.submitVoiceAttempt(1L, owner, audioFile()))
                .isInstanceOf(InvalidVoiceRecordingStateException.class);
    }

    @Test
    void submit_emptyFile_throwsInvalidAudioUpload() {
        VoiceAnswer recording = VoiceAnswer.builder().sessionQuestion(sq)
                .startedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .endedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 1, 0))
                .build();
        stored.set(recording);

        MockMultipartFile empty = new MockMultipartFile("audio", "answer.webm", "audio/webm", new byte[0]);

        assertThatThrownBy(() -> attemptService.submitVoiceAttempt(1L, owner, empty))
                .isInstanceOf(InvalidAudioUploadException.class);
    }

    @Test
    void submit_nonAudioContentType_throwsInvalidAudioUpload() {
        VoiceAnswer recording = VoiceAnswer.builder().sessionQuestion(sq)
                .startedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .endedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 1, 0))
                .build();
        stored.set(recording);

        MockMultipartFile notAudio = new MockMultipartFile("audio", "answer.txt", "text/plain", "hi".getBytes());

        assertThatThrownBy(() -> attemptService.submitVoiceAttempt(1L, owner, notAudio))
                .isInstanceOf(InvalidAudioUploadException.class);
    }

    @Test
    void submit_alreadyAnswered_throwsAttemptAlreadyExists() {
        when(attemptRepository.existsBySessionQuestionId(1L)).thenReturn(true);

        assertThatThrownBy(() -> attemptService.submitVoiceAttempt(1L, owner, audioFile()))
                .isInstanceOf(AttemptAlreadyExistsException.class);
    }
}
