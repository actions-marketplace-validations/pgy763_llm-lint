package samples.suppressed;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SuppressedSse {

    public SseEmitter stream() {
        // llm-lint:ignore LLM004
        return new SseEmitter();
    }

    public String key() {
        return "sk-proj-AbCdEf0123456789AbCdEf0123456789";
    }
}
