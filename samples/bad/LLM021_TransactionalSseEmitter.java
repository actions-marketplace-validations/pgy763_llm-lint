package demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class StreamService {

    @Transactional
    public SseEmitter stream(String prompt) {
        // 事务要等这个流结束才提交，生成多久连接就攥多久
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.send(SseEmitter.event().data(prompt));
        emitter.complete();
        return emitter;
    }
}