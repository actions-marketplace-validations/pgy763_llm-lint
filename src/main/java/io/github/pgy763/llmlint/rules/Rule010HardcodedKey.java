package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 硬编码的密钥：一旦提交，它就永远是公开的。 */
public final class Rule010HardcodedKey extends AbstractRule {

    private static final Pattern KEY = Pattern.compile(
            "^(sk-[A-Za-z0-9_-]{16,}|sk-proj-[A-Za-z0-9_-]{16,}|sk-ant-[A-Za-z0-9_-]{16,}"
                    + "|AIza[0-9A-Za-z_-]{30,}|hf_[A-Za-z0-9]{20,})$");

    @Override
    public String id() {
        return "LLM010";
    }

    @Override
    public String title() {
        return "硬编码的 API key";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return doc("/auth/密钥提交进了git历史");
    }

    @Override
    public String why() {
        return "密钥一旦提交进 git 就永远在历史里。删掉那一行不够——clone 和 fork 都还带着它，只能先吊销再换。";
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (StringLiteralExpr literal : cu.findAll(StringLiteralExpr.class)) {
            String value = literal.getValue();
            if (value.contains("${") || value.contains("***")) {
                continue;
            }
            if (KEY.matcher(value).matches()) {
                out.add(at(file, literal,
                        "这里是一个看起来真实的密钥。提交进 git 之后它就永远是公开的——"
                                + "删掉那一行、甚至 force push 都不够，历史和 fork 里还在。"
                                + "先撤销这把 key，再改成 `${LLM_API_KEY}` 或读环境变量。"));
            }
        }
        return out;
    }
}