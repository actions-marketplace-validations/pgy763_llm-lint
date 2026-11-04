package demo;

import dev.langchain4j.agent.tool.Tool;
import java.util.Map;

/** 工具只读已经准备好的内存数据，不碰数据库、不睡、不阻塞。 */
public class CurrencyTools {

    private final Map<String, String> rates;

    public CurrencyTools(Map<String, String> rates) {
        this.rates = rates;
    }

    @Tool("查某个货币对人民币的汇率")
    public String rate(String currency) {
        return rates.getOrDefault(currency, "不认识的货币");
    }
}