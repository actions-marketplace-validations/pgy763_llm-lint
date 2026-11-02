package io.github.pgy763.llmlint.config;

import io.github.pgy763.llmlint.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 找配置文件的范围：往上找、在仓库根停、找不到再从被扫的路径找。 */
class ConfigLoaderTest {

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void 从当前目录一路往上找到仓库根(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        write(repo.resolve(".llm-lint.yml"), "rules:\n  LLM001: off\n");

        Config config = ConfigLoader.discover(repo.resolve("app/src/main"), List.of(), Rules.all());

        assertEquals(repo.resolve(".llm-lint.yml").toAbsolutePath(), config.file().toAbsolutePath());
        assertEquals(RuleLevel.OFF, config.levelOf("LLM001").orElseThrow());
    }

    @Test
    void 撞到仓库根就停不读隔壁项目的配置(@TempDir Path tmp) throws Exception {
        Path outer = tmp.resolve("outer");
        Files.createDirectories(outer.resolve("proj/.git"));
        // 这份配置属于 outer，不属于 proj：proj 自己是一个仓库，往上找不该越界
        write(outer.resolve(".llm-lint.yml"), "rules:\n  LLM001: off\n");

        Config config = ConfigLoader.discover(outer.resolve("proj/app"), List.of(), Rules.all());

        assertNull(config.file());
        assertSame(Config.EMPTY, config);
    }

    @Test
    void 当前目录没有就从被扫的路径往上找(@TempDir Path tmp) throws Exception {
        Path cwd = tmp.resolve("elsewhere");
        Files.createDirectories(cwd);
        Path service = tmp.resolve("service");
        Files.createDirectories(service.resolve(".git"));
        write(service.resolve(".llm-lint.yaml"), "rules:\n  LLM002: info\n");

        Config config = ConfigLoader.discover(cwd, List.of(service), Rules.all());

        assertEquals(service.resolve(".llm-lint.yaml").toAbsolutePath(), config.file().toAbsolutePath());
    }

    @Test
    void yml排在yaml前面(@TempDir Path tmp) throws Exception {
        write(tmp.resolve(".llm-lint.yml"), "rules:\n  LLM001: off\n");
        write(tmp.resolve(".llm-lint.yaml"), "rules:\n  LLM002: off\n");

        Config config = ConfigLoader.discover(tmp, List.of(), Rules.all());

        assertEquals(".llm-lint.yml", config.file().getFileName().toString());
    }

    @Test
    void 显式给的配置文件不存在时说得明白(@TempDir Path tmp) {
        String message = assertThrows(ConfigException.class,
                () -> ConfigLoader.load(tmp.resolve("nope.yml"), Rules.all())).getMessage();
        assertTrue(message.contains("找不到配置文件"), message);
    }

    @Test
    void 显示路径时优先用相对路径(@TempDir Path tmp) {
        Path file = tmp.resolve("sub/.llm-lint.yml");
        assertEquals("sub/.llm-lint.yml", ConfigLoader.display(file, tmp));
    }
}