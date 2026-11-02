package samples.good;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LLM008_SwallowedIoException {

    private static final Logger log = LoggerFactory.getLogger(LLM008_SwallowedIoException.class);

    public void push(OutputStream out, List<String> events) {
        for (String event : events) {
            try {
                out.write((event + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                // 客户端断开是常态：记一笔就停，别继续写
                log.debug("连接已断开，停止推送: {}", e.getMessage());
                break;
            }
        }
    }
}
