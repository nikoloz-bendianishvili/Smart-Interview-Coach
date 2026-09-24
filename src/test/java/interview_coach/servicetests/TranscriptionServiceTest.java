package interview_coach.servicetests;

import interview_coach.entities.Attempt;
import interview_coach.entities.Question;
import interview_coach.entities.Session;
import interview_coach.entities.SessionQuestion;
import interview_coach.entities.VoiceAnswer;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.GradingStatus;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.events.VoiceAnswerCreatedEvent;
import interview_coach.events.VoiceAudioSubmittedEvent;
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import interview_coach.services.core.SessionService;
import interview_coach.services.core.TranscriptionService;
import interview_coach.services.storage.AudioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Covers TranscriptionService.transcribe: the Whisper-style round-trip (via a RestClient bound
 * to MockRestServiceServer, same approach as JudgeServiceTest), that success publishes
 * VoiceAnswerCreatedEvent from inside a real transaction (so AiGradingService's own
 * AFTER_COMMIT listener would actually fire on it) rather than grading being triggered
 * directly, and that a failure marks both rows FAILED and finalizes the session itself since
 * AiGradingService is never reached on that path.
 */
@ExtendWith(MockitoExtension.class)
class TranscriptionServiceTest {

    @Mock
    private VoiceAnswerRepository voiceAnswerRepository;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private AudioStorageService audioStorageService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private SessionService sessionService;

    private MockRestServiceServer mockServer;
    private TranscriptionService transcriptionService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        transcriptionService = new TranscriptionService(restClient, voiceAnswerRepository, attemptRepository,
                audioStorageService, eventPublisher, transactionManager, sessionService);
        ReflectionTestUtils.setField(transcriptionService, "model", "whisper-1");
        ReflectionTestUtils.setField(transcriptionService, "transcriptionUrl", "https://transcribe.test");
        ReflectionTestUtils.setField(transcriptionService, "apiKey", "test-key");
    }

    private VoiceAnswer voiceAnswerWithAttempt() {
        Session session = Session.builder().id(1L).status(SessionStatus.AWAITING_GRADING).build();
        Question question = Question.builder().id(1L).score(10).questionType(QuestionType.OPEN_ENDED).build();
        SessionQuestion sq = SessionQuestion.builder().id(1L).session(session).question(question).orderIndex(1).build();
        Attempt attempt = Attempt.builder().id(1L).sessionQuestion(sq).status(AttemptStatus.PENDING).build();
        VoiceAnswer voiceAnswer = VoiceAnswer.builder().id(1L).sessionQuestion(sq).attempt(attempt)
                .audioFileUrl("sq-1-abc.webm").gradingStatus(GradingStatus.PENDING).build();

        when(voiceAnswerRepository.findById(1L)).thenReturn(Optional.of(voiceAnswer));
        when(audioStorageService.load("sq-1-abc.webm")).thenReturn(new ByteArrayResource("fake-audio".getBytes()) {
            @Override
            public String getFilename() {
                return "sq-1-abc.webm";
            }
        });
        return voiceAnswer;
    }

    @Test
    void transcribe_success_savesTranscriptMirrorsOntoAttemptAndPublishesVoiceAnswerCreated() {
        VoiceAnswer voiceAnswer = voiceAnswerWithAttempt();

        mockServer.expect(requestTo("https://transcribe.test/audio/transcriptions"))
                .andRespond(withSuccess("""
                        {"text": "this is my transcribed answer"}
                        """, MediaType.APPLICATION_JSON));

        transcriptionService.transcribe(new VoiceAudioSubmittedEvent(1L));

        assertThat(voiceAnswer.getAudioTranscript()).isEqualTo("this is my transcribed answer");
        assertThat(voiceAnswer.getAttempt().getTextAnswer()).isEqualTo("this is my transcribed answer");
        // Still PENDING - AiGradingService (triggered by the event below) is what grades it.
        assertThat(voiceAnswer.getAttempt().getStatus()).isEqualTo(AttemptStatus.PENDING);

        ArgumentCaptor<VoiceAnswerCreatedEvent> captor = ArgumentCaptor.forClass(VoiceAnswerCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().voiceAnswerId()).isEqualTo(1L);

        // Success doesn't finalize itself - that's AiGradingService's job once it actually grades.
        verify(sessionService, never()).finalizeIfGradingComplete(any());

        mockServer.verify();
    }

    @Test
    void transcribe_apiFailure_marksBothFailedAndFinalizesSessionItself() {
        VoiceAnswer voiceAnswer = voiceAnswerWithAttempt();

        mockServer.expect(requestTo("https://transcribe.test/audio/transcriptions"))
                .andRespond(withServerError());

        transcriptionService.transcribe(new VoiceAudioSubmittedEvent(1L));

        assertThat(voiceAnswer.getGradingStatus()).isEqualTo(GradingStatus.FAILED);
        assertThat(voiceAnswer.getAttempt().getStatus()).isEqualTo(AttemptStatus.FAILED);

        // No transcript was ever produced, so AiGradingService is never reached on this path -
        // finalization has to happen here instead.
        verify(sessionService).finalizeIfGradingComplete(voiceAnswer.getAttempt().getSessionQuestion().getSession());
        verify(eventPublisher, never()).publishEvent(any(VoiceAnswerCreatedEvent.class));

        mockServer.verify();
    }
}
