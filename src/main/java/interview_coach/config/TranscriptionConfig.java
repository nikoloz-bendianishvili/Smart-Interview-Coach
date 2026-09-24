package interview_coach.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * A plain RestClient bean for TranscriptionService, same reasoning as Judge0Config: constructor
 * injection instead of building one inline, so tests can substitute a MockRestServiceServer-bound
 * RestClient. Named (and the injection point @Qualifier'd) rather than left to by-type
 * autowiring, since that now has two RestClient beans to choose between.
 */
@Configuration
public class TranscriptionConfig {

    @Bean
    public RestClient transcriptionRestClient() {
        return RestClient.create();
    }
}
