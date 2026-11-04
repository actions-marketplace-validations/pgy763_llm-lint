package io.github.pgy763.llmlint.report;

import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Rules;
import io.github.pgy763.llmlint.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Code Scanning 靠 partialFingerprints 认「同一条问题」，挪行号不该变成新告警。 */
class SarifReportTest {

    private static Finding at(int line, String message) {
        return new Finding("LLM001", Severity.ERROR, "src/A.java", line, 9,
                "@Transactional 里调模型", message, "https://example.com#llm001");
    }

    private static String sarif(Finding... findings) {
        return SarifReport.format(List.of(findings), Rules.all());
    }

    @Test
    void 每条结果都带指纹() {
        String out = sarif(at(10, "m"));
        assertTrue(out.contains("\"partialFingerprints\""), out);
        assertTrue(out.contains("\"llm-lint/v1\": \""), out);
    }

    @Test
    void 挪了行号指纹不变() {
        assertTrue(sarif(at(10, "m")).contains(SarifReport.fingerprint(at(10, "m"))));
        assertEquals(SarifReport.fingerprint(at(10, "m")), SarifReport.fingerprint(at(42, "m")));
    }

    @Test
    void 消息变了才算新问题() {
        assertNotEquals(SarifReport.fingerprint(at(10, "m")), SarifReport.fingerprint(at(10, "另一条消息")));
    }

    @Test
    void 指纹是十六进制短串() {
        String fp = SarifReport.fingerprint(at(10, "m"));
        assertEquals(16, fp.length());
        assertFalse(fp.contains("\""), fp);
        assertTrue(fp.matches("[0-9a-f]+"), fp);
    }
}