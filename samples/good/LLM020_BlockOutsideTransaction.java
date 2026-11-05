package demo;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class OrderService {

    /** 同样的阻塞调用，但不在事务里：连接早就还回池子了。 */
    public void refreshAll(String orderId) {
        Flux.just(orderId)
                .map(String::trim)
                .blockLast();
    }
}