package io.github.pgy763.llmlint.config;

import io.github.pgy763.llmlint.Severity;

/** 配置里能给一条规则写的四种状态；{@link #OFF} 表示这条规则根本不跑。 */
public enum RuleLevel {

    OFF(null),
    ERROR(Severity.ERROR),
    WARNING(Severity.WARNING),
    INFO(Severity.INFO);

    private final Severity severity;

    RuleLevel(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }

    public String label() {
        return this == OFF ? "off" : severity.label();
    }
}