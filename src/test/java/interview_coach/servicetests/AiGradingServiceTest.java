package interview_coach.servicetests;

import com.anthropic.client.AnthropicClient;
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
import interview_coach.repositories.AttemptRepository;
import interview_coach.repositories.VoiceAnswerRepository;
import interview_coach.services.core.AiGradingService;
import interview_coach.services.core.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focuses on the Phase 4 finalization hook, same rationale as JudgeServiceTest: whatever happens
 * to grading, finalizeIfGradingComplete must still run via the finally block. Triggered here by
 * making the very first Anthropic SDK call throw (anthropicClient.messages()), which fails fast
 * without needing to construct the real nested response shape - the actual-AI-call success path
 * remains a pre-existing untested gap, not something this test works around.
 */
@ExtendWith(MockitoExtension.class)
class AiGradingServiceTest {

    @Mock
    private AnthropicClient anthropicClient;
    @Mock
    private VoiceAnswerRepository voiceAnswerRepository;
    @Mock
    private AttemptRepository attemptRepository;
    @Mock
    private SessionService sessionService;

    private AiGradingService aiGradingService;

    @BeforeEach
    void setUp() {
        aiGradingService = new AiGradingService(anthropicClient, voiceAnswerRepository, attemptRepository, sessionService);
        ReflectionTestUtils.setField(aiGradingService, "model", "claude-sonnet-5");
    }

    private VoiceAnswer voiceAnswerFor(Session session) {
        Question question = Question.builder().id(1L).score(15).questionType(QuestionType.OPEN_ENDED)
                .statement("stmt").explanation("model answer").build();
        SessionQuestion sq = SessionQuestion.builder().id(1L).session(session).question(question).orderIndex(1).build();
        Attempt attempt = Attempt.builder().id(1L).sessionQuestion(sq).status(AttemptStatus.PENDING).build();
        return VoiceAnswer.builder().id(1L).attempt(attempt).audioTranscript("user answer").gradingStatus(GradingStatus.PENDING).build();
    }

    @Test
    void gradeAnswer_aiCallFails_marksFailedAndStillFinalizesSession() {
        Session session = Session.builder().id(2L).status(SessionStatus.AWAITING_GRADING).build();
        VoiceAnswer voiceAnswer = voiceAnswerFor(session);
        when(voiceAnswerRepository.findById(1L)).thenReturn(Optional.of(voiceAnswer));
        when(anthropicClient.messages()).thenThrow(new RuntimeException("AI API unavailable"));

        aiGradingService.gradeAnswer(new VoiceAnswerCreatedEvent(1L));

        assertThat(voiceAnswer.getGradingStatus()).isEqualTo(GradingStatus.FAILED);
        assertThat(voiceAnswer.getAttempt().getStatus()).isEqualTo(AttemptStatus.FAILED);

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(sessionService).finalizeIfGradingComplete(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getId()).isEqualTo(2L);
    }
}
