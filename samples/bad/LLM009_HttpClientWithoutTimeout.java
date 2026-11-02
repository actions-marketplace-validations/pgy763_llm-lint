package samples.bad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class LLM009_HttpClientWithoutTimeout {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
