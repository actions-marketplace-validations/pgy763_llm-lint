package samples.bad;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class LLM008_SwallowedIoException {

    public void push(OutputStream out, List<String> events) {
        for (String event : events) {
            try {
                out.write((event + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
            }
        }
    }
}
