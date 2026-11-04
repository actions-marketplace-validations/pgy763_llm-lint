package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Rule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SARIF 2.1.0：给 GitHub Code Scanning 吃，问题会出现在仓库的 Security 页签与 PR 的
 * 「Code scanning results」里，而不是只刷一遍日志。
 */
public final class SarifReport {

    private SarifReport() {
    }

    public static String format(List<Finding> findings, List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n")
                .append("  \"$schema\": \"https://json.schemastore.org/sarif-2.1.0.json\",\n")
                .append("  \"version\": \"2.1.0\",\n")
                .append("  \"runs\": [\n")
                .append("    {\n")
                .append("      \"tool\": {\n")
                .append("        \"driver\": {\n")
                .append("          \"name\": \"llm-lint\",\n")
                .append("          \"informationUri\": \"https://github.com/pgy763/llm-lint\",\n")
                .append("          \"rules\": [\n");
        for (int i = 0; i < rules.size(); i++) {
            Rule r = rules.get(i);
            sb.append("            {\n")
                    .append("              \"id\": \"").append(JsonReport.escape(r.id())).append("\",\n")
                    .append("              \"name\": \"").append(JsonReport.escape(r.title())).append("\",\n")
                    .append("              \"shortDescription\": {\"text\": \"")
                    .append(JsonReport.escape(r.title())).append("\"},\n")
                    .append("              \"fullDescription\": {\"text\": \"")
                    .append(JsonReport.escape(r.why().isEmpty() ? r.title() : r.why())).append("\"},\n")
                    .append("              \"helpUri\": \"").append(JsonReport.escape(r.docUrl())).append("\",\n")
                    .append("              \"defaultConfiguration\": {\"level\": \"")
                    .append(level(r.severity().name())).append("\"}\n")
                    .append("            }").append(i < rules.size() - 1 ? "," : "").append('\n');
        }
        sb.append("          ]\n")
                .append("        }\n")
                .append("      },\n")
                .append("      \"results\": [\n");
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("        {\n")
                    .append("          \"ruleId\": \"").append(JsonReport.escape(f.ruleId())).append("\",\n")
                    .append("          \"partialFingerprints\": {\"llm-lint/v1\": \"")
                    .append(fingerprint(f)).append("\"},\n")
                    .append("          \"level\": \"").append(level(f.severity().name())).append("\",\n")
                    .append("          \"message\": {\"text\": \"")
                    .append(JsonReport.escape(f.title() + "：" + f.message())).append("\"},\n")
                    .append("          \"locations\": [\n")
                    .append("            {\n")
                    .append("              \"physicalLocation\": {\n")
                    .append("                \"artifactLocation\": {\"uri\": \"")
                    .append(JsonReport.escape(f.file())).append("\"},\n")
                    .append("                \"region\": {\"startLine\": ").append(Math.max(1, f.line()));
            if (f.column() > 0) {
                sb.append(", \"startColumn\": ").append(f.column());
            }
            sb.append("}\n")
                    .append("              }\n")
                    .append("            }\n")
                    .append("          ]\n")
                    .append("        }").append(i < findings.size() - 1 ? "," : "").append('\n');
        }
        sb.append("      ]\n")
                .append("    }\n")
                .append("  ]\n")
                .append("}\n");
        return sb.toString();
    }

    /**
     * Code Scanning 用这个指纹把「同一条问题」跨提交认成同一个告警，而不是每跑一次多一条。
     * 只算 规则 + 文件 + 消息：挪了行、加了空行都还是同一条；消息变了才算新的。
     */
    static String fingerprint(Finding f) {
        String key = f.ruleId() + (char) 0x1F + f.file() + (char) 0x1F + f.message();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 里没有 SHA-256？", e);
        }
    }

    private static String level(String severity) {
        return switch (severity) {
            case "ERROR" -> "error";
            case "WARNING" -> "warning";
            default -> "note";
        };
    }

    /** 给外面用的统计，免得再解析一遍。 */
    public static Map<String, Integer> countByRule(List<Finding> findings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Finding f : findings) {
            counts.merge(f.ruleId(), 1, Integer::sum);
        }
        return counts;
    }
}