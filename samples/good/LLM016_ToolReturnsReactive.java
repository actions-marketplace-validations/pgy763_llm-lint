package samples.good;

import org.springframework.ai.tool.annotation.Tool;

public class LLM016_ToolReturnsReactive {

    /** 工具方法直接给结果，模型拿到的就是「已发货」这三个字。 */
    @Tool(description = "查订单状态")
    public String orderStatus(String orderId) {
        return "已发货";
    }
}