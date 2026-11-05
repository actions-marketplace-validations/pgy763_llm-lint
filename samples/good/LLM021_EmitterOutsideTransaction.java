package demo;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class StreamService {

    /** 同样的流式返回，但不带事务：连接用完就还回去。 */
    public SseEmitter stream(String prompt) {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.send(SseEmitter.event().data(prompt));
        emitter.complete();
        return emitter;
    }
}