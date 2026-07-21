package com.xyoo.helper.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Qdrant 向量库客户端（基于 REST 接口，端口默认 6333）。
 * <p>
 * 采用 REST 自封装而非第三方 Java client，避免额外依赖与版本 API 漂移风险。
 * 向量以 base64 编码的 little-endian float32 传输（Qdrant REST 规范要求）。
 * </p>
 */
@Service
public class QdrantService {

    private static final Logger log = LoggerFactory.getLogger(QdrantService.class);

    private final QdrantProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public QdrantService(QdrantProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    private String base() {
        return props.getUrl().replaceAll("/+$", "") + "/collections/" + props.getCollection();
    }

    /** 确保集合存在（幂等，已存在则跳过） */
    public void ensureCollection() {
        String url = base();
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", props.getDim());
        vectors.put("distance", "Cosine");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", vectors);
        try {
            restTemplate.put(url, body);
            log.info("Qdrant 集合 {} 已创建", props.getCollection());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.info("Qdrant 集合 {} 已存在，跳过创建", props.getCollection());
            } else {
                log.warn("初始化 Qdrant 集合失败（{}）：{}", e.getStatusCode(), e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            log.warn("初始化 Qdrant 集合异常: {}", e.getMessage());
        }
    }

    /** 批量写入向量（幂等：point id 由 docId+序号派生，重复写入即覆盖） */
    public void upsert(List<QdrantPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        String url = base() + "/points?wait=true";
        List<Object> ps = new ArrayList<>();
        for (QdrantPoint p : points) {
            Map<String, Object> m = new LinkedHashMap<>();
            // Qdrant REST 的点 id 为裸 UUID 字符串（不是 {"uuid": ...} 的 gRPC 包装格式）
            m.put("id", p.getId());
            // 向量直接以浮点数组提交（Qdrant 该版本 /points 端点不识别 encoding=base64）
            m.put("vector", p.getVector());
            m.put("payload", p.getPayload());
            ps.add(m);
        }
        Map<String, Object> body = Map.of("points", ps);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.put(url, new HttpEntity<>(body, headers));
        } catch (Exception e) {
            log.error("Qdrant upsert 失败: {}", e.getMessage());
            throw new RuntimeException("向量写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 向量检索，按 moduleId 集合过滤（RBAC）。allowedModuleIds 为空表示无权限，返回空。
     */
    public List<QdrantHit> search(List<Float> queryVector, Set<Long> allowedModuleIds, int limit) {
        if (allowedModuleIds == null || allowedModuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        String url = base() + "/points/search";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", queryVector);
        body.put("limit", limit);
        body.put("with_payload", true);

        Map<String, Object> must = new LinkedHashMap<>();
        must.put("key", "moduleId");
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("any", new ArrayList<>(allowedModuleIds));
        must.put("match", match);
        body.put("filter", Map.of("must", List.of(must)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            String resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            return parseHits(resp);
        } catch (Exception e) {
            log.error("Qdrant search 失败: {}", e.getMessage());
            throw new RuntimeException("向量检索失败: " + e.getMessage(), e);
        }
    }

    /** 按 docId 删除该文档的全部切片向量 */
    public void deleteByDocId(String docId) {
        String url = base() + "/points/delete?wait=true";
        Map<String, Object> must = new LinkedHashMap<>();
        must.put("key", "docId");
        must.put("match", Map.of("value", docId));
        Map<String, Object> body = Map.of("filter", Map.of("must", List.of(must)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.warn("Qdrant 删除文档 {} 切片失败（可忽略）: {}", docId, e.getMessage());
        }
    }

    private List<QdrantHit> parseHits(String resp) throws Exception {
        List<QdrantHit> hits = new ArrayList<>();
        JsonNode root = objectMapper.readTree(resp);
        JsonNode result = root.get("result");
        if (result != null && result.isArray()) {
            for (JsonNode n : result) {
                QdrantHit hit = new QdrantHit();
                JsonNode payload = n.get("payload");
                if (payload != null) {
                    if (payload.has("content")) hit.setContent(payload.get("content").asText());
                    if (payload.has("docId")) hit.setDocId(payload.get("docId").asText());
                    if (payload.has("docTitle")) hit.setDocTitle(payload.get("docTitle").asText());
                    if (payload.has("moduleId")) hit.setModuleId(payload.get("moduleId").asLong());
                }
                JsonNode score = n.get("score");
                if (score != null) hit.setScore(score.asDouble());
                hits.add(hit);
            }
        }
        return hits;
    }

    // ==================== 数据传输对象 ====================

    /** 写入 Qdrant 的一个点 */
    public static class QdrantPoint {
        private String id;
        private List<Float> vector;
        private Map<String, Object> payload;

        public QdrantPoint() {
        }

        public QdrantPoint(String id, List<Float> vector, Map<String, Object> payload) {
            this.id = id;
            this.vector = vector;
            this.payload = payload;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<Float> getVector() {
            return vector;
        }

        public void setVector(List<Float> vector) {
            this.vector = vector;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }

        public void setPayload(Map<String, Object> payload) {
            this.payload = payload;
        }
    }

    /** 检索返回的一个命中片段 */
    public static class QdrantHit {
        private String content;
        private String docId;
        private String docTitle;
        private Long moduleId;
        private double score;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocTitle() {
            return docTitle;
        }

        public void setDocTitle(String docTitle) {
            this.docTitle = docTitle;
        }

        public Long getModuleId() {
            return moduleId;
        }

        public void setModuleId(Long moduleId) {
            this.moduleId = moduleId;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }
}
