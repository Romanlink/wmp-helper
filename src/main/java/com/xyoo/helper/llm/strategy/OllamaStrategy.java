package com.xyoo.helper.llm.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyoo.helper.llm.LlmProperties;
import com.xyoo.helper.llm.LlmStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 本地 Ollama 大模型策略（原生 /api/generate 协议）。
 *
 * <p>完整保留原有调用逻辑：NDJSON 逐行读取，提取 response 字段增量输出，
 * 遇 done=true 结束。与在线模型实现互不干扰，可经 helper.llm.provider=ollama 切换。</p>
 */
@Service
public class OllamaStrategy implements LlmStrategy {

    private static final Logger log = LoggerFactory.getLogger(OllamaStrategy.class);

    private final LlmProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public OllamaStrategy(LlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        // 长连接流式读取，单独配置 ReadTimeout（默认 5 分钟，避免生成中被中断）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(300000);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters().add(
                new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled();
    }

    @Override
    public String disabledMessage() {
        return "当前未启用本地大模型（Ollama）。请在配置中开启 helper.llm.enabled=true。";
    }

    @Override
    public void streamChat(String message, Consumer<String> tokenConsumer) throws IOException {
        String url = props.getBaseUrl().replaceAll("/+$", "") + "/api/generate";

        // 构造请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("prompt", message == null ? "" : message);
        body.put("stream", true);

        byte[] requestBody;
        try {
            requestBody = objectMapper.writeValueAsBytes(body);
        } catch (IOException e) {
            log.error("构造 Ollama 请求体失败", e);
            tokenConsumer.accept("抱歉，请求构造失败，请稍后重试。");
            return;
        }

        try {
            restTemplate.execute(url, HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request.getBody().write(requestBody);
                    },
                    response -> {
                        // 逐行读取 NDJSON 流，提取 response 字段增量输出
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().isEmpty()) {
                                    continue;
                                }
                                try {
                                    JsonNode node = objectMapper.readTree(line);
                                    JsonNode respNode = node.get("response");
                                    if (respNode != null && !respNode.isNull()) {
                                        tokenConsumer.accept(respNode.asText());
                                    }
                                    JsonNode doneNode = node.get("done");
                                    if (doneNode != null && doneNode.asBoolean(false)) {
                                        break;
                                    }
                                } catch (Exception parseEx) {
                                    // 跳过无法解析的行（心跳/字段缺失等）
                                    log.debug("跳过无法解析的 Ollama 响应行: {}", line);
                                }
                            }
                        }
                        return null;
                    });
        } catch (ResourceAccessException e) {
            log.warn("连接本地 Ollama 失败: {}", e.getMessage());
            throw new IOException("无法连接本地 Ollama 服务（" + url
                    + "），请确认 Ollama 已启动且模型 [" + props.getModel() + "] 已加载。", e);
        }
    }
}
