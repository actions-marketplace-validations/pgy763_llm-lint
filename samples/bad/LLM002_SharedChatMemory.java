package samples.bad;

import org.springframework.stereotype.Service;

@Service
public class LLM002_SharedChatMemory {

    private final ChatMemory chatMemory = new MessageWindowChatMemory(20);

    public String chat(String user, String text) {
        chatMemory.add(user, text);
        return text;
    }
}
