package samples.good;

import org.springframework.stereotype.Service;

@Service
public class LLM003_ChatClientInjection {

    private final ChatClient chatClient;

    /** 注入的是 Builder，ChatClient 是自己 build 出来的。 */
    public LLM003_ChatClientInjection(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String ask(String question) {
        return chatClient.prompt().user(question).call().content();
    }
}
