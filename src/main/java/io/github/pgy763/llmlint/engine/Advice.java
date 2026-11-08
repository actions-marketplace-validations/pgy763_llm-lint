package io.github.pgy763.llmlint.engine;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.lang.Messages;

import java.util.List;

/**
 * 没有现成补丁、但改法很明确的那几条规则，给一句人能照着做的话。
 *
 * <p>{@code --fix} 只对 LLM006、LLM018 这类「换一个词就行」的问题出补丁。
 * LLM019（工具方法里阻塞）和 LLM020（事务里等异步结果）要挪代码位置，
 * 机器改容易改坏，所以这边只给建议、不动文件 —— 想自动改的人看完这句也知道往哪改。
 */
public final class Advice {

    private Advice() {
    }

    /** 这条规则有没有改法建议；没有返回 null。 */
    public static String forRule(String ruleId) {
        return switch (ruleId) {
            case "LLM019" -> Messages.t(
                    "把这次阻塞调用挪到工具方法外面：工具方法只组装参数，查询交给调用它的服务方法去做"
                            + "（或者丢给 @Async 的线程池）。模型在等工具的时候，别让它顺便占着一个请求线程。",
                    "Move the blocking call out of the tool method: let the tool assemble the arguments only, "
                            + "and do the lookup in the service that calls it (or hand it to an @Async pool). "
                            + "The model is already waiting on the tool; do not also pin a request thread.");
            case "LLM020" -> Messages.t(
                    "别在事务里等：先把异步结果在事务外取好再传进来；或者把这段挪到提交之后，"
                            + "用 TransactionSynchronizationManager.registerSynchronization(...) 在 afterCommit 里做。",
                    "Do not wait inside the transaction: fetch the async result before the transaction starts "
                            + "and pass it in, or move the work to after commit via "
                            + "TransactionSynchronizationManager.registerSynchronization(...).");
            default -> null;
        };
    }

    /** 把能建议的都写成一段；没有就返回空串。 */
    public static String render(List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        for (Finding finding : findings) {
            if (finding.fix() != null) {
                continue;
            }
            String advice = forRule(finding.ruleId());
            if (advice == null) {
                continue;
            }
            sb.append(Messages.f("建议 %s %s:%d:%d%n", "Advice %s %s:%d:%d%n",
                    finding.ruleId(), finding.file(), finding.line(), finding.column()));
            sb.append("  ").append(advice).append('\n');
        }
        if (!sb.isEmpty()) {
            sb.append('\n');
        }
        return sb.toString();
    }
}