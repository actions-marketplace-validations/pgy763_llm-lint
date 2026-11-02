package io.github.pgy763.llmlint;

/**
 * 一处可以自动改的源码位置：把 [begin, end] 这段换成 {@code replacement}。
 *
 * <p>行列都从 1 开始，和 javaparser 里 {@code Node.getRange()} 的口径一致——
 * {@code end} 指的是这段源码的<b>最后一个字符</b>，所以换的时候要把它也算进去。
 * 只给「能保证语义不变」的规则配补丁（比如补一个 produces），
 * 拿不准的宁可不出补丁，也不要猜。
 *
 * @param beginLine    起始行
 * @param beginColumn  起始列
 * @param endLine      结束行
 * @param endColumn    结束列（含）
 * @param replacement  换上去的源码
 */
public record Fix(int beginLine, int beginColumn, int endLine, int endColumn, String replacement) {
}