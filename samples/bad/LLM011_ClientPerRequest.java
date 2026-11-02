package samples.bad;

import org.springframework.stereotype.Service;

@Service
public class LLM011_ClientPerRequest {

    private final ChatModel chatModel;

    public LLM011_ClientPerRequest(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String answer(String question) {
        ChatClient client = ChatClient.create(chatModel);
        return client.prompt().user(question).call().content();
    }
}
