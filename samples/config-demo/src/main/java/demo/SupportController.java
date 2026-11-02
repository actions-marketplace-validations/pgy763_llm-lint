package demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SupportController {

    private final SupportAssistant assistant;

    public SupportController(SupportAssistant assistant) {
        this.assistant = assistant;
    }

    /** 前端用 EventSource 订阅；超时由网关统一控制，所以这里故意没有传。 */
    @GetMapping("/tickets/stream")
    public SseEmitter stream() {
        return new SseEmitter();
    }

    @GetMapping("/tickets/summary")
    public String summary() {
        return assistant.summarize("把工单 42 的详情整理成 JSON");
    }
}