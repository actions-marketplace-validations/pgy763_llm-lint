package samples.good;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLM012_AiServicesPerRequest {

    @Bean
    public Assistant assistant(ChatLanguageModel model) {
        return AiServices.create(Assistant.class, model);
    }

    interface Assistant {
        String chat(String question);
    }
}
