package samples.good;

import org.springframework.stereotype.Service;

@Service
public class LLM007_SmallMaxTokens {

    /** 工具调用要把参数写成 JSON，预算留够。 */
    public String ask(ChatClient.Builder builder) {
        return builder.build()
                .prompt()
                .user("把订单 123 的详情整理成 JSON")
                .options(ChatOptions.builder().maxTokens(2048).build())
                .call()
                .content();
    }
}
