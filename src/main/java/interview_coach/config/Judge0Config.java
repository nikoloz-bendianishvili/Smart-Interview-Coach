package interview_coach.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * A plain RestClient bean so JudgeService can take it via constructor injection instead of
 * building its own (RestClient.create()) inline - the same reason AnthropicClient is a bean
 * rather than being constructed inside AiGradingService. Lets tests substitute a
 * MockRestServiceServer-bound RestClient instead of needing a live Judge0 instance.
 */
@Configuration
public class Judge0Config {

    @Bean
    public RestClient judge0RestClient() {
        return RestClient.create();
    }
}
