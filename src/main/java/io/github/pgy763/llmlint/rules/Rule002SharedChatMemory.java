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
import io.github.pgy763.llmlint.lang.Messages;
import io.github.pgy763.llmlint.support.Ast;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 单例里放一份 ChatMemory：A 的名字会出现在 B 的对话里。 */
public final class Rule002SharedChatMemory extends AbstractRule {

    @Override
    public String id() {
        return "LLM002";
    }

    @Override
    public String title() {
        return Messages.t("单例里共用一份 ChatMemory", "Shared ChatMemory in a singleton");
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String docUrl() {
        return doc("/java/多用户共用一份ChatMemory");
    }

    @Override
    public String why() {
        return Messages.t(
                "ChatMemory 里装的是对话。单例 Bean 只 new 一份，所有用户往里写，A 的提问就成了 B 的上下文——这类串话在压测里看不出来。",
                "ChatMemory holds the conversation. A singleton bean creates it once and every user"
                        + " writes into it, so A's question becomes B's context — cross-talk like this"
                        + " never shows up in load tests.");
    }

    @Override
    public List<Finding> check(Path file, CompilationUnit cu) {
        List<Finding> out = new ArrayList<>();
        for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (c.isInterface()) {
                continue;
            }
            if (!Ast.hasAnnotation(c, "Service", "Component", "Repository",
                    "RestController", "Controller")) {
                continue;
            }
            for (FieldDeclaration f : c.getFields()) {
                for (VariableDeclarator v : f.getVariables()) {
                    if (Ast.isMemoryType(Ast.simpleTypeName(v.getType()))) {
                        out.add(at(file, v, Messages.f(
                                "`%s` 是个单例 Bean，而 `%s` 是它身上唯一的会话记忆。"
                                        + "所有用户共用这一份，A 说过的名字会出现在 B 的上下文里。"
                                        + "改成按会话 id 从 ChatMemoryProvider/Store 取，或用 MessageChatMemoryAdvisor 按会话隔离。",
                                "`%s` is a singleton bean and `%s` is its only conversation memory."
                                        + " All users share it, so a name A mentions shows up in"
                                        + " B's context. Fetch memory per session from"
                                        + " ChatMemoryProvider/Store, or isolate per session with"
                                        + " MessageChatMemoryAdvisor.",
                                c.getNameAsString(), v.getNameAsString())));
                    }
                }
            }
            for (ConstructorDeclaration ctor : c.getConstructors()) {
                for (Parameter p : ctor.getParameters()) {
                    if (Ast.isMemoryType(Ast.simpleTypeName(p.getType()))) {
                        out.add(at(file, p, Messages.f(
                                "`%s` 的构造参数里有 `%s`，而这个 Bean 是单例——注入进来的就是同一份记忆，"
                                        + "多用户会串台。",
                                "`%s` takes `%s` as a constructor parameter, and this bean is a"
                                        + " singleton — the injected instance is the same memory, so"
                                        + " multiple users bleed into each other.",
                                c.getNameAsString(), p.getTypeAsString())));
                    }
                }
            }
        }
        return out;
    }
}