package samples.bad;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LLM013_ModelCallInLoop {

    private final EmbeddingModel embeddingModel;

    public LLM013_ModelCallInLoop(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<float[]> vectors(List<String> texts) {
        List<float[]> out = new ArrayList<>();
        for (String text : texts) {
            out.add(embeddingModel.embed(text));
        }
        return out;
    }
}
