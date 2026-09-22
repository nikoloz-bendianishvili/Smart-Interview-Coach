package interview_coach.entities;

import interview_coach.enums.GradingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "voice_answers")
public class VoiceAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Null until submit. The row is created earlier, at voice/start, when there is no Attempt
     * yet to attach it to - see AttemptService.startVoiceRecording.
     */
    @OneToOne
    @JoinColumn(name = "attempt_id", unique = true)
    private Attempt attempt;

    /**
     * Known from voice/start onward, unlike attempt - lets a recording be looked up (and its
     * timing validated) before the answer is ever submitted.
     */
    @ManyToOne
    @JoinColumn(name = "session_question_id", nullable = false)
    private SessionQuestion sessionQuestion;

    /**
     * Server-recorded timing from the voice/start and voice/stop endpoints - never the client's
     * own clock. submitOpenEndedAttempt computes timeTakenSeconds from these rather than
     * trusting whatever the client sends, whenever they're both present.
     */
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @Column(columnDefinition = "TEXT")
    private String audioTranscript;

    private String audioFileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GradingStatus gradingStatus;

    private Double aiScore;

    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
