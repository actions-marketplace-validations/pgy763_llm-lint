package demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DigestJob {

    private final ChatClient chatClient;

    public DigestJob(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void dailyDigest() {
        // 这里没有请求，也就没有会话：ChatMemory 要么串台，要么每次新建
        String text = chatClient.prompt("总结一下今天的工单").call().content();
        System.out.println(text);
    }
}