package samples.good;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
public class LLM005_BlockInStreamingMethod {

    /** 一路流下去，不把结果收成 List。 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream() {
        return Flux.just("a", "b").map(String::toUpperCase);
    }
}
