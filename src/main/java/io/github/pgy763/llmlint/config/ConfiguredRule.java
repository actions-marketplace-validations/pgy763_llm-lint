package io.github.pgy763.llmlint.config;

import com.github.javaparser.ast.CompilationUnit;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.Severity;

import java.nio.file.Path;
import java.util.List;

/**
 * 配置改过级别的规则：检查逻辑还是原来那条，只是吐出来的每条结果换上新级别。
 *
 * <p>包装在规则这一层而不是在最后改 findings，{@code --list-rules}、SARIF 的
 * {@code defaultConfiguration}、退出码判断才会和实际报告一致——一处改，处处一致。
 */
final class ConfiguredRule implements Rule {

    private final Rule delegate;
    private final Severity severity;

    ConfiguredRule(Rule delegate, Severity severity) {
        this.delegate = delegate;
        this.severity = severity;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String title() {
        return delegate.title();
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public String docUrl() {
        return delegate.docUrl();
    }

    @Override
    public String framework() {
        return delegate.framework();
    }

    @Override
    public String why() {
        return delegate.why();
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        return delegate.check(file, cu).stream()
                .map(f -> new Finding(f.ruleId(), severity, f.file(), f.line(), f.column(),
                        f.title(), f.message(), f.docUrl()))
                .toList();
    }
}