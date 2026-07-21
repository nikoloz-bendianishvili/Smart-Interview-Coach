package interview_coach.entities;

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
@Table(name = "attempts")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "session_question_id", nullable = false, unique = true)
    private SessionQuestion sessionQuestion;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Integer selectedOption;

    @Column(columnDefinition = "TEXT")
    private String textAnswer;

    private Boolean isCorrect;

    private Integer score;

    private Integer timeTakenSeconds;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
