package samples.good;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LLM015_ReactiveInTransaction {

    private final RowRepository rowRepository;

    public LLM015_ReactiveInTransaction(RowRepository rowRepository) {
        this.rowRepository = rowRepository;
    }

    /** 事务里只做同步的事，提交后再拼流。 */
    @Transactional
    public void saveAll(List<String> rows) {
        rowRepository.saveAll(rows);
    }
}
