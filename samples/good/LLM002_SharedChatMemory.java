package samples.good;

import org.springframework.stereotype.Service;

@Service
public class LLM002_SharedChatMemory {

    private final ChatMemoryProvider provider;

    public LLM002_SharedChatMemory(ChatMemoryProvider provider) {
        this.provider = provider;
    }

    /** 每个会话一条记忆，不共享。 */
    public String chat(String conversationId, String user, String text) {
        ChatMemory memory = (ChatMemory) provider.get(conversationId);
        memory.add(user, text);
        return text;
    }
}
