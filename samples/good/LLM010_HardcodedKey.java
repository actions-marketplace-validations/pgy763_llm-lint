package samples.good;

import org.springframework.context.annotation.Configuration;

@Configuration
public class LLM010_HardcodedKey {

    private final String apiKey = System.getenv("LLM_API_KEY");

    public String key() {
        return apiKey;
    }
}
