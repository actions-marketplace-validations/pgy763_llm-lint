package samples.bad;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LLM001_TransactionalModelCall {

    private final ChatModel chatModel;

    public LLM001_TransactionalModelCall(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Transactional
    public String answer(String question) {
        ChatResponse response = chatModel.call(new Prompt(question));
        return response.getResult().getOutput().getText();
    }
}
