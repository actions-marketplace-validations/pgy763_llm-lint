package samples.good;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LLM001_TransactionalModelCall {

    private final ChatModel chatModel;

    public LLM001_TransactionalModelCall(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** 模型调用在事务外面——几秒的等待不占用数据库连接。 */
    public String answer(String question) {
        ChatResponse response = chatModel.call(new Prompt(question));
        return response.getResult().getOutput().getText();
    }

    @Transactional
    public void save(String question, String answer) {
        // 只写库，不碰模型
    }
}
