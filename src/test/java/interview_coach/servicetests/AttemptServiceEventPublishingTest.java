package interview_coach.servicetests;

import interview_coach.entities.*;
import interview_coach.enums.AttemptStatus;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionStatus;
import interview_coach.events.CodeSubmissionCreatedEvent;
import interview_coach.events.VoiceAnswerCreatedEvent;
import interview_coach.repositories.*;
import interview_coach.services.core.AttemptService;
import interview_coach.services.core.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that AttemptService triggers grading by publishing an
 * ApplicationEvent from inside the transactional submit methods, rather than
 * calling the @Async grader beans directly (see JudgeService / AiGradingService,
 * which are @TransactionalEventListener(AFTER_COMMIT) handlers of these events).
 */
@ExtendWith(MockitoExtension.class)
class AttemptServiceEventPublishingTest {

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

    private AttemptService attemptService;

    private User user;

    @BeforeEach
    void setUp() {
        attemptService = new AttemptService(
                attemptRepository,
                optionRepository,
                sessionQuestionRepository,
                codeSubmissionRepository,
                voiceAnswerRepository,
                eventPublisher,
                sessionService,
                Clock.systemDefaultZone()
        );

        user = User.builder().id(1L).build();
    }

    private SessionQuestion buildOwnedSessionQuestion(QuestionType questionType) {
        Session session = Session.builder().id(1L).user(user).status(SessionStatus.IN_PROGRESS).build();
        Question question = Question.builder().id(1L).score(10).questionType(questionType).build();
        return SessionQuestion.builder().id(1L).session(session).question(question).orderIndex(1).build();
    }

    @Test
    void submitCodingAttempt_publishesCodeSubmissionCreatedEventWithSavedId() {
        when(sessionQuestionRepository.findById(1L)).thenReturn(java.util.Optional.of(buildOwnedSessionQuestion(QuestionType.CODING)));
        when(attemptRepository.save(any(Attempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(codeSubmissionRepository.save(any(CodeSubmission.class))).thenAnswer(inv -> {
            CodeSubmission submission = inv.getArgument(0);
            submission.setId(42L);
            return submission;
        });

        attemptService.submitCodingAttempt(1L, user, "public class Solution {}", 60);

        ArgumentCaptor<CodeSubmissionCreatedEvent> captor = ArgumentCaptor.forClass(CodeSubmissionCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().submissionId()).isEqualTo(42L);
    }

    @Test
    void submitOpenEndedAttempt_publishesVoiceAnswerCreatedEventWithSavedId() {
        when(sessionQuestionRepository.findById(1L)).thenReturn(java.util.Optional.of(buildOwnedSessionQuestion(QuestionType.OPEN_ENDED)));
        when(attemptRepository.save(any(Attempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voiceAnswerRepository.save(any(VoiceAnswer.class))).thenAnswer(inv -> {
            VoiceAnswer voiceAnswer = inv.getArgument(0);
            voiceAnswer.setId(99L);
            return voiceAnswer;
        });

        attemptService.submitOpenEndedAttempt(1L, user, "My answer text", 45);

        ArgumentCaptor<VoiceAnswerCreatedEvent> captor = ArgumentCaptor.forClass(VoiceAnswerCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().voiceAnswerId()).isEqualTo(99L);
    }

    @Test
    void submitMCQAttempt_doesNotPublishAnyEvent() {
        when(sessionQuestionRepository.findById(1L)).thenReturn(java.util.Optional.of(buildOwnedSessionQuestion(QuestionType.MCQ)));
        Option option = Option.builder()
                .option1("A").option2("B").option3("C").option4("D")
                .correctOption(2)
                .build();
        when(optionRepository.findByQuestionId(1L)).thenReturn(java.util.Optional.of(option));
        when(attemptRepository.save(any(Attempt.class))).thenAnswer(inv -> inv.getArgument(0));

        Attempt attempt = attemptService.submitMCQAttempt(1L, user, 2, 30);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        verifyNoInteractions(eventPublisher);
    }
}