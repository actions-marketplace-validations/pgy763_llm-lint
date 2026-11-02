package samples.bad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class LLM004_SseEmitterDefaultTimeout {

    @GetMapping("/stream")
    public SseEmitter stream() {
        return new SseEmitter();
    }
}
