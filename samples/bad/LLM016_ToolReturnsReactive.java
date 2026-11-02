package samples.bad;

import org.springframework.ai.tool.annotation.Tool;

import reactor.core.publisher.Mono;

public class LLM016_ToolReturnsReactive {

    /** 工具方法返回 Mono：框架不会替你订阅，模型拿到的是个对象。 */
    @Tool(description = "查订单状态")
    public Mono<String> orderStatus(String orderId) {
        return Mono.just("shipped");
    }
}