package com.xyoo.helper.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检索服务 —— 将用户问题向量化后在 Qdrant 中检索，并按角色可见菜单过滤。
 * <p>
 * RBAC 过滤在 Qdrant 的 payload filter 层完成（moduleId IN 当前角色可见菜单），
 * 复用现有角色-菜单映射，无需新建权限表，且前端无法越权检索到其他模块的文档。
 * </p>
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final RagProperties ragProperties;

    public RetrievalService(EmbeddingService embeddingService,
                            QdrantService qdrantService,
                            RagProperties ragProperties) {
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.ragProperties = ragProperties;
    }

    /**
     * 检索与问题最相关的文档片段（已按角色可见菜单过滤）。
     *
     * @param query             用户问题
     * @param allowedModuleIds  当前角色可见菜单ID集合；为空表示无权限，返回空
     * @return 命中的片段（含内容、所属文档、标题、相似度分数）
     */
    public List<RetrievedChunk> retrieve(String query, Set<Long> allowedModuleIds) {
        if (query == null || query.isBlank() || allowedModuleIds == null || allowedModuleIds.isEmpty()) {
            return List.of();
        }
        List<Float> vec = embeddingService.embed(query);
        if (vec.isEmpty()) {
            return List.of();
        }
        List<QdrantService.QdrantHit> hits = qdrantService.search(vec, allowedModuleIds, ragProperties.getTopK());
        return hits.stream()
                .map(h -> new RetrievedChunk(h.getContent(), h.getDocId(), h.getDocTitle(), h.getModuleId(), h.getScore()))
                .collect(Collectors.toList());
    }

    /** 检索命中的片段 */
    public static class RetrievedChunk {
        private final String content;
        private final String docId;
        private final String docTitle;
        private final Long moduleId;
        private final double score;

        public RetrievedChunk(String content, String docId, String docTitle, Long moduleId, double score) {
            this.content = content;
            this.docId = docId;
            this.docTitle = docTitle;
            this.moduleId = moduleId;
            this.score = score;
        }

        public String getContent() {
            return content;
        }

        public String getDocId() {
            return docId;
        }

        public String getDocTitle() {
            return docTitle;
        }

        public Long getModuleId() {
            return moduleId;
        }

        public double getScore() {
            return score;
        }
    }
}
