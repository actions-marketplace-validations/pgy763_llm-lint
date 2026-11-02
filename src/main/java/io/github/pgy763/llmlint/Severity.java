package io.github.pgy763.llmlint;

/** 严重级别：ERROR 会让 CI 红，WARNING 只提示。 */
public enum Severity {
    ERROR,
    WARNING,
    INFO;

    public String label() {
        return switch (this) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFO -> "info";
        };
    }
}