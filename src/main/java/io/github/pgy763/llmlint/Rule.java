package io.github.pgy763.llmlint;

import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.List;

/** 一条规则：给一个 Java 文件，吐 0..n 个问题。 */
public interface Rule {

    String id();

    String title();

    Severity severity();

    /** 手册里对应的那一条，找不到原文就指到分类首页。 */
    String docUrl();

    /**
     * 这块坑主要出现在哪个框架上：spring-ai、langchain4j，或者 both。
     * 只用于展示（--list-rules / --explain），不参与判断。
     */
    default String framework() {
        return "both";
    }

    /** 索引表里那一列「拦的是什么」：比 title 长一点，一句话。不写就用 title。 */
    default String summary() {
        return title();
    }

    /** 一两句话说清「为什么这是问题」，--explain 会用它。 */
    default String why() {
        return "";
    }

    List<Finding> check(Path file, CompilationUnit cu);
}