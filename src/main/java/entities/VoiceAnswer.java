package entities;

import enums.GradingStatus;
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

    @OneToOne
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private Attempt attempt;

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
