package interview_coach.entities;

import interview_coach.enums.InteractionMode;
import interview_coach.enums.QuestionType;
import interview_coach.enums.SessionType;
import interview_coach.enums.SessionStatus;
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
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionType sessionType;

    @Enumerated(EnumType.STRING)
    private InteractionMode interactionMode;

    @ManyToOne
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    private Integer numOfQuestions;

    private Integer timeLimitMinutes;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer totalScore;

    /**
     * Computed once at start (SessionService.startSession) from the SessionType's multiplier
     * over either the chosen time or the selected questions' summed timeLimit. Enforcement adds
     * a grace period on top of this at check time (effectiveDeadline), never stored here.
     */
    private LocalDateTime sessionDeadline;

    /**
     * True only when this session was closed because sessionDeadline passed, as opposed to the
     * user completing it manually (with or without attempts still pending grading).
     */
    @Builder.Default
    private boolean endedByTimeout = false;
}
