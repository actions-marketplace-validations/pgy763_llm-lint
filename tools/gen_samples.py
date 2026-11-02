"""生成 samples/bad 与 samples/good 二十个文件。

bad 里每个文件只该触发它文件名前缀那一条规则；good 里是同一处的正确写法。
这两组文件是规则唯一的验收标准：不再需要 Spring AI 的 jar 就能跑。
"""

import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "samples"

FILES = {}

# ---------- LLM001 事务里调模型 ----------
FILES["bad/LLM001_TransactionalModelCall.java"] = '''package samples.bad;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LLM001_TransactionalModelCall {

    private final ChatModel chatModel;

    public LLM001_TransactionalModelCall(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Transactional
    public String answer(String question) {
        ChatResponse response = chatModel.call(new Prompt(question));
        return response.getResult().getOutput().getText();
    }
}
'''

FILES["good/LLM001_TransactionalModelCall.java"] = '''package samples.good;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LLM001_TransactionalModelCall {

    private final ChatModel chatModel;

    public LLM001_TransactionalModelCall(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** 模型调用在事务外面——几秒的等待不占用数据库连接。 */
    public String answer(String question) {
        ChatResponse response = chatModel.call(new Prompt(question));
        return response.getResult().getOutput().getText();
    }

    @Transactional
    public void save(String question, String answer) {
        // 只写库，不碰模型
    }
}
'''

# ---------- LLM002 单例里一份 ChatMemory ----------
FILES["bad/LLM002_SharedChatMemory.java"] = '''package samples.bad;

import org.springframework.stereotype.Service;

@Service
public class LLM002_SharedChatMemory {

    private final ChatMemory chatMemory = new MessageWindowChatMemory(20);

    public String chat(String user, String text) {
        chatMemory.add(user, text);
        return text;
    }
}
'''

FILES["good/LLM002_SharedChatMemory.java"] = '''package samples.good;

import org.springframework.stereotype.Service;

@Service
public class LLM002_SharedChatMemory {

    private final ChatMemoryProvider provider;

    public LLM002_SharedChatMemory(ChatMemoryProvider provider) {
        this.provider = provider;
    }

    /** 每个会话一条记忆，不共享。 */
    public String chat(String conversationId, String user, String text) {
        ChatMemory memory = (ChatMemory) provider.get(conversationId);
        memory.add(user, text);
        return text;
    }
}
'''

# ---------- LLM003 让容器注入 ChatClient ----------
FILES["bad/LLM003_ChatClientInjection.java"] = '''package samples.bad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LLM003_ChatClientInjection {

    @Autowired
    private ChatClient chatClient;

    public String ask(String question) {
        return chatClient.prompt().user(question).call().content();
    }
}
'''

FILES["good/LLM003_ChatClientInjection.java"] = '''package samples.good;

import org.springframework.stereotype.Service;

@Service
public class LLM003_ChatClientInjection {

    private final ChatClient chatClient;

    /** 注入的是 Builder，ChatClient 是自己 build 出来的。 */
    public LLM003_ChatClientInjection(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String ask(String question) {
        return chatClient.prompt().user(question).call().content();
    }
}
'''

# ---------- LLM004 SseEmitter 没设超时 ----------
FILES["bad/LLM004_SseEmitterDefaultTimeout.java"] = '''package samples.bad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class LLM004_SseEmitterDefaultTimeout {

    @GetMapping("/stream")
    public SseEmitter stream() {
        return new SseEmitter();
    }
}
'''

FILES["good/LLM004_SseEmitterDefaultTimeout.java"] = '''package samples.good;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class LLM004_SseEmitterDefaultTimeout {

    @GetMapping("/stream")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(180_000L);
        emitter.onTimeout(emitter::complete);
        return emitter;
    }
}
'''

# ---------- LLM005 流式方法里 block ----------
FILES["bad/LLM005_BlockInStreamingMethod.java"] = '''package samples.bad;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
public class LLM005_BlockInStreamingMethod {

    @GetMapping("/stream")
    public Flux<String> stream() {
        List<String> all = Flux.just("a", "b").collectList().block();
        return Flux.fromIterable(all);
    }
}
'''

FILES["good/LLM005_BlockInStreamingMethod.java"] = '''package samples.good;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
public class LLM005_BlockInStreamingMethod {

    /** 一路流下去，不把结果收成 List。 */
    @GetMapping("/stream")
    public Flux<String> stream() {
        return Flux.just("a", "b").map(String::toUpperCase);
    }
}
'''

# ---------- LLM006 produces 少了 charset ----------
FILES["bad/LLM006_SseCharset.java"] = '''package samples.bad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class LLM006_SseCharset {

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return new SseEmitter(0L);
    }
}
'''

FILES["good/LLM006_SseCharset.java"] = '''package samples.good;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class LLM006_SseCharset {

    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream() {
        return new SseEmitter(0L);
    }
}
'''

# ---------- LLM007 maxTokens 太小 ----------
FILES["bad/LLM007_SmallMaxTokens.java"] = '''package samples.bad;

import org.springframework.stereotype.Service;

@Service
public class LLM007_SmallMaxTokens {

    public String ask(ChatClient.Builder builder) {
        return builder.build()
                .prompt()
                .user("把订单 123 的详情整理成 JSON")
                .options(ChatOptions.builder().maxTokens(128).build())
                .call()
                .content();
    }
}
'''

FILES["good/LLM007_SmallMaxTokens.java"] = '''package samples.good;

import org.springframework.stereotype.Service;

@Service
public class LLM007_SmallMaxTokens {

    /** 工具调用要把参数写成 JSON，预算留够。 */
    public String ask(ChatClient.Builder builder) {
        return builder.build()
                .prompt()
                .user("把订单 123 的详情整理成 JSON")
                .options(ChatOptions.builder().maxTokens(2048).build())
                .call()
                .content();
    }
}
'''

# ---------- LLM008 空的 catch 吞掉 IOException ----------
FILES["bad/LLM008_SwallowedIoException.java"] = '''package samples.bad;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class LLM008_SwallowedIoException {

    public void push(OutputStream out, List<String> events) {
        for (String event : events) {
            try {
                out.write((event + "\\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
            }
        }
    }
}
'''

FILES["good/LLM008_SwallowedIoException.java"] = '''package samples.good;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LLM008_SwallowedIoException {

    private static final Logger log = LoggerFactory.getLogger(LLM008_SwallowedIoException.class);

    public void push(OutputStream out, List<String> events) {
        for (String event : events) {
            try {
                out.write((event + "\\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                // 客户端断开是常态：记一笔就停，别继续写
                log.debug("连接已断开，停止推送: {}", e.getMessage());
                break;
            }
        }
    }
}
'''

# ---------- LLM009 @Bean 建客户端没配超时 ----------
FILES["bad/LLM009_HttpClientWithoutTimeout.java"] = '''package samples.bad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class LLM009_HttpClientWithoutTimeout {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
'''

FILES["good/LLM009_HttpClientWithoutTimeout.java"] = '''package samples.good;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class LLM009_HttpClientWithoutTimeout {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(60_000);
        return new RestTemplate(factory);
    }
}
'''

# ---------- LLM010 硬编码密钥 ----------
FILES["bad/LLM010_HardcodedKey.java"] = '''package samples.bad;

import org.springframework.context.annotation.Configuration;

@Configuration
public class LLM010_HardcodedKey {

    private static final String API_KEY = "sk-proj-AbCdEf0123456789AbCdEf0123456789";

    public String key() {
        return API_KEY;
    }
}
'''

FILES["good/LLM010_HardcodedKey.java"] = '''package samples.good;

import org.springframework.context.annotation.Configuration;

@Configuration
public class LLM010_HardcodedKey {

    private final String apiKey = System.getenv("LLM_API_KEY");

    public String key() {
        return apiKey;
    }
}
'''

for rel, content in FILES.items():
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")

print(f"写入 {len(FILES)} 个文件到 {ROOT}")