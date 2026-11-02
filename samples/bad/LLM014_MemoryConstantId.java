package samples.bad;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import org.springframework.stereotype.Service;

@Service
public class LLM014_MemoryConstantId {

    private final ChatMemoryProvider memoryProvider;

    public LLM014_MemoryConstantId(ChatMemoryProvider memoryProvider) {
        this.memoryProvider = memoryProvider;
    }

    public ChatMemory memoryFor(String userId) {
        return memoryProvider.get("default");
    }
}
