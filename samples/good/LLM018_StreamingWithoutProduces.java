package samples.good;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
public class LLM018_StreamingWithoutProduces {

    /** 写清楚 produces，Spring 才会一个 chunk 一个 chunk 往外发。 */
    @GetMapping(value = "/tickets/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream() {
        return Flux.just("a", "b");
    }
}