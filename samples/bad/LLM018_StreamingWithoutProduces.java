package samples.bad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
public class LLM018_StreamingWithoutProduces {

    /** 返回 Flux 却没写 produces：Spring 按 JSON 收完再发。 */
    @GetMapping("/tickets/stream")
    public Flux<String> stream() {
        return Flux.just("a", "b");
    }
}