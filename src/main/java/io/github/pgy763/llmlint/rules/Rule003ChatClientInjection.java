package io.github.pgy763.llmlint.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import io.github.pgy763.llmlint.AbstractRule;
import io.github.pgy763.llmlint.Finding;
import io.github.pgy763.llmlint.Severity;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 让容器注入 ChatClient：Spring AI 只注册 ChatClient.Builder，启动就 NoSuchBeanDefinitionException。
 *
 * <p>只认「真的在让容器注入」的写法——带 @Autowired/@Resource/@Inject 的字段，
 * 或者构造参数。自己 {@code builder.build()} 存到字段里的不算。
 */
public final class Rule003ChatClientInjection extends AbstractRule {

    @Override
    public String id() {
        return "LLM003";
    }

    @Override
    public String title() {
        return "让容器注入 ChatClient";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return doc("/java/注入ChatClient找不到Bean");
    }

    @Override
    public String why() {
        return "Spring AI 的自动配置只注册 ChatClient.Builder 和 ChatModel，没有 ChatClient 这种 Bean。注入它启动就失败，报的还只是「找不到 Bean」。";
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        boolean springBean = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(c -> !c.isInterface()
                        && Ast.hasAnnotation(c, "Service", "Component", "Repository",
                        "RestController", "Controller", "Configuration"));
        if (!springBean) {
            return none();
        }

        List<Finding> out = new ArrayList<>();
        for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
            if (!Ast.hasAnnotation(f, "Autowired", "Resource", "Inject")) {
                continue;
            }
            for (VariableDeclarator v : f.getVariables()) {
                if ("ChatClient".equals(typeName(v.getTypeAsString()))) {
                    out.add(at(file, v,
                            "字段 `" + v.getNameAsString() + "` 让容器注入 `ChatClient`。"
                                    + "Spring AI 的自动配置只注册 `ChatClient.Builder`（以及 ChatModel），"
                                    + "没有 `ChatClient`——启动就是 `NoSuchBeanDefinitionException`。"
                                    + "注入 `ChatClient.Builder`，在构造里 `build()`；"
                                    + "或者自己定义一个 `@Bean ChatClient`。"));
                }
            }
        }
        for (ConstructorDeclaration ctor : cu.findAll(ConstructorDeclaration.class)) {
            for (Parameter p : ctor.getParameters()) {
                if ("ChatClient".equals(typeName(p.getTypeAsString()))) {
                    out.add(at(file, p,
                            "构造参数 `" + p.getNameAsString() + "` 要的是 `ChatClient`，"
                                    + "容器里只有 `ChatClient.Builder`。换成 Builder 再 build。"));
                }
            }
        }
        return out;
    }

    private static String typeName(String raw) {
        int lt = raw.indexOf('<');
        return lt > 0 ? raw.substring(0, lt) : raw;
    }
}