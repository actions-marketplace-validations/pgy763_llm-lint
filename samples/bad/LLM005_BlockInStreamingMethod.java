package samples.bad;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
public class LLM005_BlockInStreamingMethod {

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream() {
        List<String> all = Flux.just("a", "b").collectList().block();
        return Flux.fromIterable(all);
    }
}
