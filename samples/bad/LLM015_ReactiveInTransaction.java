package samples.bad;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LLM015_ReactiveInTransaction {

    private final RowRepository rowRepository;

    public LLM015_ReactiveInTransaction(RowRepository rowRepository) {
        this.rowRepository = rowRepository;
    }

    @Transactional
    public Flux<String> saveAll(List<String> rows) {
        rowRepository.saveAll(rows);
        return Flux.fromIterable(rows).map(String::toUpperCase);
    }
}
