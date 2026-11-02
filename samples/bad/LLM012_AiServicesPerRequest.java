package samples.bad;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Service;

@Service
public class LLM012_AiServicesPerRequest {

    private final ChatLanguageModel model;

    public LLM012_AiServicesPerRequest(ChatLanguageModel model) {
        this.model = model;
    }

    public Assistant assistant() {
        return AiServices.create(Assistant.class, model);
    }

    interface Assistant {
        String chat(String question);
    }
}
