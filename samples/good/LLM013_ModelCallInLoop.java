package samples.good;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LLM013_ModelCallInLoop {

    private final EmbeddingModel embeddingModel;

    public LLM013_ModelCallInLoop(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** 一次带走：N 条文本只有一次往返。 */
    public List<float[]> vectors(List<String> texts) {
        List<float[]> out = new ArrayList<>();
        out.addAll(embeddingModel.embedAll(texts));
        return out;
    }
}
