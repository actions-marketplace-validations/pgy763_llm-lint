package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;

import java.util.List;

/**
 * GitHub Actions 注解格式：直接被界面渲染成「文件第 N 行」的红色错误。
 *
 * @see <a href="https://docs.github.com/actions/using-workflows/workflow-commands-for-github-actions">workflow commands</a>
 */
public final class GitHubReport {

    private GitHubReport() {
    }

    public static String format(List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        for (Finding f : findings) {
            String level = f.severity().label();
            sb.append("::").append(level)
                    .append(" file=").append(prop(f.file()))
                    .append(",line=").append(f.line())
                    .append(",title=").append(prop(f.ruleId() + " " + f.title()))
                    .append("::").append(data(f.message()));
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 注解属性里逗号、冒号、百分号都得转义，否则会被切开。 */
    private static String prop(String s) {
        return s.replace("%", "%25")
                .replace("\r", "%0D")
                .replace("\n", "%0A")
                .replace(",", "%2C")
                .replace(":", "%3A");
    }

    private static String data(String s) {
        return s.replace("%", "%25")
                .replace("\r", "%0D")
                .replace("\n", "%0A");
    }
}