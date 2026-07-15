package com.xyoo.helper.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议基类（DeepSeek / Qwen 在线大模型均兼容此协议）。
 *
 * <p>请求：POST {base-url}/chat/completions，带 Bearer 鉴权，messages 结构。
 * 响应：SSE 流，每行 {@code data: {choices:[{delta:{content:"..."}}]}}，结束为 {@code data: [DONE]}。
 * DeepSeek / Qwen 仅需继承本类并声明 provider 名称即可，协议解析逻辑完全复用，互不干扰。</p>
 */
public abstract class AbstractOpenAiStrategy implements LlmStrategy {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final LlmProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    protected AbstractOpenAiStrategy(LlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        // 长连接流式读取，单独配置 ReadTimeout（默认 5 分钟，避免生成中被中断）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(300000);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters().add(
                new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    /** chat/completions 路径，子类可覆盖（如带前缀的兼容地址） */
    protected String completionsPath() {
        return "/chat/completions";
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled()
                && props.getApiKey() != null
                && !props.getApiKey().isBlank();
    }

    @Override
    public String disabledMessage() {
        return "当前模型 [" + provider() + "] 未启用或缺少 API Key。请在配置中设置 helper.llm.api-key。";
    }

    @Override
    public void streamChat(String message, Consumer<String> tokenConsumer) throws IOException {
        if (!isEnabled()) {
            tokenConsumer.accept(disabledMessage());
            return;
        }

        String url = props.getBaseUrl().replaceAll("/+$", "") + completionsPath();

        // 构造 OpenAI 兼容请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("stream", true);
        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", message == null ? "" : message);
        body.put("messages", List.of(userMsg));

        byte[] requestBody;
        try {
            requestBody = objectMapper.writeValueAsBytes(body);
        } catch (IOException e) {
            log.error("构造 {} 请求体失败", provider(), e);
            tokenConsumer.accept("抱歉，请求构造失败，请稍后重试。");
            return;
        }

        try {
            restTemplate.execute(url, HttpMethod.POST,
                    request -> {
                        HttpHeaders headers = request.getHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setBearerAuth(props.getApiKey());
                    },
                    response -> {
                        // 逐行读取 SSE 流，提取 choices[0].delta.content 增量输出
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().isEmpty()) {
                                    continue;
                                }
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring(5).strip();
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    JsonNode choices = node.get("choices");
                                    if (choices != null && choices.isArray() && choices.size() > 0) {
                                        JsonNode delta = choices.get(0).get("delta");
                                        if (delta != null) {
                                            JsonNode content = delta.get("content");
                                            if (content != null && !content.isNull()) {
                                                tokenConsumer.accept(content.asText());
                                            }
                                        }
                                    }
                                } catch (Exception parseEx) {
                                    // 跳过无法解析的行（心跳/字段缺失等）
                                    log.debug("跳过无法解析的 {} 响应行: {}", provider(), line);
                                }
                            }
                        }
                        return null;
                    });
        } catch (HttpStatusCodeException e) {
            log.warn("{} 接口返回错误: {}", provider(), e.getStatusCode());
            throw new IOException(provider() + " 接口调用失败（" + e.getStatusCode() + "）："
                    + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            log.warn("连接 {} 失败: {}", provider(), e.getMessage());
            throw new IOException("无法连接 " + provider() + " 服务（" + url
                    + "），请确认地址可访问且网络通畅。", e);
        }
    }
}
