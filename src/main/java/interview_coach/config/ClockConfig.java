package interview_coach.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable Clock so every "now" used by the session-timing feature (deadline
 * computation, expiry checks, the scheduled sweep, voice-answer timestamps) goes through one
 * source of time - tests substitute Clock.fixed(...) instead of sleeping or racing the wall
 * clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
