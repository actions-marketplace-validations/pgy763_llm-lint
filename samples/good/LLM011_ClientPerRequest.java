package samples.good;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLM011_ClientPerRequest {

    /** 建一次，全应用复用：连接池、TLS、序列化器都只初始化一次。 */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
