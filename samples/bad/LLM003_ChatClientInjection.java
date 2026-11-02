package samples.bad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LLM003_ChatClientInjection {

    @Autowired
    private ChatClient chatClient;

    public String ask(String question) {
        return chatClient.prompt().user(question).call().content();
    }
}
