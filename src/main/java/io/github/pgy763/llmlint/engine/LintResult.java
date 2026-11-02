package io.github.pgy763.llmlint.engine;

import io.github.pgy763.llmlint.Finding;

import java.util.List;

/**
 * 一次扫描的结果。
 *
 * @param findings     报出来的问题（已过滤抑制注释）
 * @param filesScanned 真正解析过的文件数
 * @param filesSkipped 解析失败跳过的文件数
 * @param suppressed   被注释抑制掉、没报出来的数量
 */
public record LintResult(List<Finding> findings, int filesScanned, int filesSkipped, int suppressed) {
}