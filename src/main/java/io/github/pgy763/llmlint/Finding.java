package io.github.pgy763.llmlint;

/**
 * 一条检查结果。
 *
 * @param ruleId  规则编号，如 LLM001
 * @param severity 严重级别
 * @param file    文件路径（相对调用者给的路径，/ 分隔）
 * @param line    行号，1 起
 * @param column  列号，1 起；认不出来时是 0
 * @param title   一句话说清是什么问题
 * @param message 说明为什么是问题、怎么改
 * @param docUrl  手册里对应的那条
 * @param fix     能自动修的话，改哪儿、换成什么；不能修时是 null
 */
public record Finding(
        String ruleId,
        Severity severity,
        String file,
        int line,
        int column,
        String title,
        String message,
        String docUrl,
        Fix fix) {

    /** 不能自动修的写法：大多数规则都用这个。 */
    public Finding(String ruleId, Severity severity, String file, int line, int column,
                   String title, String message, String docUrl) {
        this(ruleId, severity, file, line, column, title, message, docUrl, null);
    }
}