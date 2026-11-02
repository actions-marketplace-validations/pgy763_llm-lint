package samples.bad;

import org.springframework.context.annotation.Configuration;

@Configuration
public class LLM010_HardcodedKey {

    private static final String API_KEY = "sk-proj-AbCdEf0123456789AbCdEf0123456789";

    public String key() {
        return API_KEY;
    }
}
