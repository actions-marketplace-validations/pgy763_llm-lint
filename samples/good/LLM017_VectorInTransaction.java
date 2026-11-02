package samples.good;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class LLM017_VectorInTransaction {

    private final VectorStore vectorStore;

    public LLM017_VectorInTransaction(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 嵌入是一次网络往返，放在事务外面做，别占着连接。 */
    public void ingest(String body) {
        vectorStore.add(List.of(new Document(body)));
    }
}