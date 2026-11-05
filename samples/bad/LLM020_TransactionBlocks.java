package demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Service
public class OrderService {

    @Transactional
    public void refreshAll(String orderId) {
        // 事务还开着，这里却停下来等流跑完
        Flux.just(orderId)
                .map(String::trim)
                .blockLast();
    }
}