package io.github.pgy763.llmlint.config;

import io.github.pgy763.llmlint.Rule;
import io.github.pgy763.llmlint.lang.Messages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 找配置文件、读它、编译成 {@link Config}，以及生成一份带注释的模板。 */
public final class ConfigLoader {

    /** 认这两个名字，.yml 优先。 */
    static final List<String> FILE_NAMES = List.of(".llm-lint.yml", ".llm-lint.yaml");

    private ConfigLoader() {
    }

    /**
     * 先看当前目录，一路往上找到仓库根（含 {@code .git} 的那一级）为止；还找不到，再从
     * 被扫描的路径往上找。都没有就返回空配置——这时候行为和没有这个功能时完全一样。
     */
    public static Config discover(Path cwd, List<Path> roots, List<Rule> known) throws ConfigException {
        Path base = cwd.toAbsolutePath().normalize();
        Path found = findFrom(base);
        if (found == null) {
            for (Path root : roots) {
                found = findFrom(base.resolve(root).normalize());
                if (found != null) {
                    break;
                }
            }
        }
        return found == null ? Config.EMPTY : load(found, known);
    }

    /** 从 start（目录或文件）往上找；撞到仓库根就不再往上，免得读到隔壁项目的配置。 */
    static Path findFrom(Path start) {
        Path dir = start.toAbsolutePath().normalize();
        if (Files.isRegularFile(dir)) {
            dir = dir.getParent();
        }
        while (dir != null) {
            for (String name : FILE_NAMES) {
                Path candidate = dir.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
            if (Files.isDirectory(dir.resolve(".git"))) {
                return null;
            }
            dir = dir.getParent();
        }
        return null;
    }

    public static Config load(Path file, List<Rule> known) throws ConfigException {
        if (!Files.isRegularFile(file)) {
            throw new ConfigException(Messages.f("找不到配置文件：%s（--config 后面要跟文件路径）",
                    "Config file not found: %s (--config needs a file path)", file));
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigException("读不了配置文件 " + file + "：" + e.getMessage());
        }
        MiniYaml.Mapping root = MiniYaml.parse(text, file.toString());
        return Config.from(root, file, known);
    }

    /** {@code --init} 写出去的那份：全是不生效的注释，用户按需去掉 #。 */
    public static String template(List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("# yaml-language-server: $schema=https://raw.githubusercontent.com/pgy763/llm-lint/main/docs/llm-lint.schema.json\n");
        sb.append(Messages.t(
                "# .llm-lint.yml —— 仓库里的检查配置：开关、排除路径、级别都写在这里。\n",
                "# .llm-lint.yml - the repo's lint config: rule switches, excluded paths, levels.\n"));
        sb.append(Messages.t(
                "# 放仓库根目录，CI 和本地读的是同一份，不用每次在流水线里手写参数。\n",
                "# Keep it at the repo root so CI and your laptop read the same file.\n"));
        sb.append("#\n");
        sb.append(Messages.t(
                "# 优先级：命令行参数 > 这个文件 > 默认值。\n",
                "# Precedence: command line > this file > defaults.\n"));
        sb.append(Messages.t(
                "# 级别：off（不跑）/ error（卡 CI）/ warning / info。\n",
                "# Levels: off (do not run) / error (fails CI) / warning / info.\n"));
        sb.append("\nversion: 1\n\nrules:\n");
        for (Rule rule : rules) {
            sb.append("  # ").append(rule.id()).append(' ').append(rule.title())
                    .append(Messages.f("（默认 %s）", " (default %s)", rule.severity().label())).append('\n');
        }
        sb.append(Messages.t(
                "  # LLM009: off        # 超时在网关里统一配过了，这里是误报\n",
                "  # LLM009: off        # timeouts are set in the gateway, this one is a false positive\n"));
        sb.append(Messages.t(
                "  # LLM007: error      # 我们要求 maxTokens 至少 1024，默认的 warning 不够狠\n",
                "  # LLM007: error      # we require maxTokens >= 1024, the default warning is not strict enough\n"));
        sb.append("\nexclude:\n");
        sb.append("  # - \"**/generated/**\"\n");
        sb.append("  # - \"**/test/java/**\"\n");
        sb.append(Messages.t(
                "\n# fail-on: error        # error（默认）/ warning / never\n",
                "\n# fail-on: error        # error (default) / warning / never\n"));
        sb.append(Messages.t(
                "# min-severity: info    # 低于这个级别的不报，也不计\n",
                "# min-severity: info    # findings below this level are dropped entirely\n"));
        sb.append(Messages.t(
                "# baseline: .llm-lint.baseline   # 存量问题的清单，写了就只报新问题，见 docs/baseline.md\n",
                "# baseline: .llm-lint.baseline   # accepted old findings; only new ones get reported, see docs/baseline.md\n"));
        return sb.toString();
    }

    /** 给人看的路径：在仓库里的显示成相对路径，不在的话保留原样（统一用 /）。 */
    public static String display(Path file, Path cwd) {
        Path base = cwd.toAbsolutePath().normalize();
        try {
            Path relative = base.relativize(file.toAbsolutePath().normalize());
            String text = relative.toString().replace('\\', '/');
            if (!text.startsWith("..") && !text.isEmpty()) {
                return text;
            }
        } catch (IllegalArgumentException ignored) {
            // 不同盘符，没法相对化
        }
        return file.toString().replace('\\', '/');
    }
}