package samples.good;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import org.springframework.stereotype.Service;

@Service
public class LLM014_MemoryConstantId {

    private final ChatMemoryProvider memoryProvider;

    public LLM014_MemoryConstantId(ChatMemoryProvider memoryProvider) {
        this.memoryProvider = memoryProvider;
    }

    /** id 从请求里来：每个会话一份记忆。 */
    public ChatMemory memoryFor(String userId) {
        return memoryProvider.get(userId);
    }
}
