package demo;

import dev.langchain4j.agent.tool.Tool;

/** 工具方法里查库：模型在等工具，工具在等数据库。 */
public class OrderTools {

    private final OrderRepository orderRepository;

    public OrderTools(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Tool("查订单现在到哪了")
    public String orderStatus(String orderId) {
        Order order = orderRepository.findById(orderId);
        return order == null ? "没有这个订单" : order.status();
    }
}