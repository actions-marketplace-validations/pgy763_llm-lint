package demo;

import org.springframework.stereotype.Service;

@Service
public class SupportAssistant {

    private final ChatClient chatClient;

    public SupportAssistant(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String summarize(String ticket) {
        return chatClient.prompt()
                .user(ticket)
                .options(ChatOptions.builder().maxTokens(128).build())
                .call()
                .content();
    }
}