package samples.bad;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LLM017_VectorInTransaction {

    private final VectorStore vectorStore;

    public LLM017_VectorInTransaction(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 事务里写向量：嵌入是一次网络往返，数据库连接一直占着。 */
    @Transactional
    public void ingest(String title, String body) {
        vectorStore.add(List.of(new Document(body)));
    }
}