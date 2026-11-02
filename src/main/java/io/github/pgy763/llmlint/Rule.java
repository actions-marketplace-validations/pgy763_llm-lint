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

    List<Finding> check(Path file, CompilationUnit cu);
}