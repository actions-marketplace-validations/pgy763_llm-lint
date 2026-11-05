package io.github.pgy763.llmlint;

import io.github.pgy763.llmlint.rules.Rule001TransactionalModelCall;
import io.github.pgy763.llmlint.rules.Rule002SharedChatMemory;
import io.github.pgy763.llmlint.rules.Rule003ChatClientInjection;
import io.github.pgy763.llmlint.rules.Rule004SseEmitterDefaultTimeout;
import io.github.pgy763.llmlint.rules.Rule005BlockInStreamingMethod;
import io.github.pgy763.llmlint.rules.Rule006SseCharset;
import io.github.pgy763.llmlint.rules.Rule007SmallMaxTokens;
import io.github.pgy763.llmlint.rules.Rule008SwallowedIoException;
import io.github.pgy763.llmlint.rules.Rule009HttpClientWithoutTimeout;
import io.github.pgy763.llmlint.rules.Rule010HardcodedKey;
import io.github.pgy763.llmlint.rules.Rule011ClientPerRequest;
import io.github.pgy763.llmlint.rules.Rule012AiServicesPerRequest;
import io.github.pgy763.llmlint.rules.Rule013ModelCallInLoop;
import io.github.pgy763.llmlint.rules.Rule014ChatMemoryConstantId;
import io.github.pgy763.llmlint.rules.Rule015ReactiveInTransaction;
import io.github.pgy763.llmlint.rules.Rule016ToolReturnsReactive;
import io.github.pgy763.llmlint.rules.Rule017VectorInTransaction;
import io.github.pgy763.llmlint.rules.Rule018StreamingWithoutProduces;
import io.github.pgy763.llmlint.rules.Rule019ToolBlocks;
import io.github.pgy763.llmlint.rules.Rule020TransactionBlocks;

import java.util.List;

/** 规则清单。顺序就是输出顺序，也是手册里的顺序。 */
public final class Rules {

    private Rules() {
    }

    public static List<Rule> all() {
        return List.of(
                new Rule001TransactionalModelCall(),
                new Rule002SharedChatMemory(),
                new Rule003ChatClientInjection(),
                new Rule004SseEmitterDefaultTimeout(),
                new Rule005BlockInStreamingMethod(),
                new Rule006SseCharset(),
                new Rule007SmallMaxTokens(),
                new Rule008SwallowedIoException(),
                new Rule009HttpClientWithoutTimeout(),
                new Rule010HardcodedKey(),
                new Rule011ClientPerRequest(),
                new Rule012AiServicesPerRequest(),
                new Rule013ModelCallInLoop(),
                new Rule014ChatMemoryConstantId(),
                new Rule015ReactiveInTransaction(),
                new Rule016ToolReturnsReactive(),
                new Rule017VectorInTransaction(),
                new Rule018StreamingWithoutProduces(),
                new Rule019ToolBlocks(),
                new Rule020TransactionBlocks());
    }
}