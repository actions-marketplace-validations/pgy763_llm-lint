package demo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DigestJob {

    private final DigestQueue queue;

    public DigestJob(DigestQueue queue) {
        this.queue = queue;
    }

    /** 定时任务只决定「要做什么」，模型调用留给有请求上下文的路径。 */
    @Scheduled(cron = "0 0 8 * * *")
    public void enqueueDailyDigest() {
        queue.add("总结一下今天的工单");
    }
}