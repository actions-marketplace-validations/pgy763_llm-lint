package samples.bad;

import org.springframework.stereotype.Service;

@Service
public class LLM007_SmallMaxTokens {

    public String ask(ChatClient.Builder builder) {
        return builder.build()
                .prompt()
                .user("把订单 123 的详情整理成 JSON")
                .options(ChatOptions.builder().maxTokens(128).build())
                .call()
                .content();
    }
}
