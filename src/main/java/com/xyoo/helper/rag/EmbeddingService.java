package com.xyoo.helper.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding 服务 —— 将文本转为向量。
 * <p>
 * 当前实现调用本地 Ollama 的 {@code /api/embed} 接口（bge-m3，1024 维）。
 * 若后续切换为云端 embedding，只需在此处替换 provider 调用，对外接口不变。
 * </p>
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public EmbeddingService(EmbeddingProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 将单条文本转为向量（float 列表）。空文本返回空列表。
     */
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String url = props.getBaseUrl().replaceAll("/+$", "") + "/api/embed";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            String resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(resp);
            JsonNode embeddings = root.get("embeddings");
            if (embeddings != null && embeddings.isArray() && embeddings.size() > 0) {
                JsonNode vec = embeddings.get(0);
                List<Float> result = new ArrayList<>();
                for (JsonNode n : vec) {
                    result.add((float) n.asDouble());
                }
                return result;
            }
            throw new IllegalStateException("Ollama embed 返回格式异常: " + resp);
        } catch (Exception e) {
            log.error("调用 embedding 服务失败: {}", e.getMessage());
            throw new RuntimeException("文本向量化失败: " + e.getMessage(), e);
        }
    }

    /** 批量向量化（逐条调用，便于在日志中定位失败文档） */
    public List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(embed(t));
        }
        return out;
    }
}
