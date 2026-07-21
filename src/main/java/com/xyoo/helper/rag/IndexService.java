package com.xyoo.helper.rag;

import com.xyoo.helper.entity.DocChunk;
import com.xyoo.helper.entity.DocInfo;
import com.xyoo.helper.repository.DocChunkRepository;
import com.xyoo.helper.repository.DocInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 索引编排服务 —— 将文档内容切片、向量化并写入 Qdrant，同时持久化切片元数据。
 * <p>
 * 设计原则：索引失败不影响文档本身的增删改（所有异常在方法内吞掉并记日志），
 * 保证「文档可正常保存」优先于「可被检索」。
 * </p>
 */
@Service
public class IndexService {

    private static final Logger log = LoggerFactory.getLogger(IndexService.class);

    private final DocInfoRepository docInfoRepository;
    private final DocChunkRepository docChunkRepository;
    private final EmbeddingService embeddingService;
    private final DocChunker docChunker;
    private final QdrantService qdrantService;
    private final EmbeddingProperties embeddingProperties;

    public IndexService(DocInfoRepository docInfoRepository,
                        DocChunkRepository docChunkRepository,
                        EmbeddingService embeddingService,
                        DocChunker docChunker,
                        QdrantService qdrantService,
                        EmbeddingProperties embeddingProperties) {
        this.docInfoRepository = docInfoRepository;
        this.docChunkRepository = docChunkRepository;
        this.embeddingService = embeddingService;
        this.docChunker = docChunker;
        this.qdrantService = qdrantService;
        this.embeddingProperties = embeddingProperties;
    }

    /** 当前索引版本标识（模型名 + 维度），用于后续模型升级时全量重建 */
    private String embeddingVersion() {
        return embeddingProperties.getModel() + "@" + embeddingProperties.getDim();
    }

    /**
     * 为单个文档建立/重建向量索引（幂等）。
     * 切片后用稳定派生的 point id 写入 Qdrant，重复调用即覆盖更新。
     */
    public void indexDocument(DocInfo doc) {
        try {
            if (doc == null || doc.getDocContent() == null || doc.getDocContent().isBlank()) {
                log.debug("文档 {} 无正文内容，跳过索引", doc.getDocId());
                return;
            }
            String docId = doc.getDocId();
            Long moduleId = doc.getModuleId();
            String title = doc.getDocTitle();
            String tags = doc.getDocTags();

            // 1. 清旧：删除数据库切片记录 + Qdrant 中该文档的全部点
            docChunkRepository.deleteByDocId(docId);
            qdrantService.deleteByDocId(docId);

            // 2. 切片
            List<String> chunks = docChunker.chunk(doc.getDocContent());
            if (chunks.isEmpty()) {
                return;
            }

            // 3. 逐片向量化并构建写入对象
            List<QdrantService.QdrantPoint> points = new ArrayList<>();
            List<DocChunk> entities = new ArrayList<>();
            int idx = 0;
            for (String c : chunks) {
                List<Float> vec = embeddingService.embed(c);
                if (vec.isEmpty()) {
                    continue;
                }
                String pointId = UUID.nameUUIDFromBytes(
                        ("doc_chunk:" + docId + "#" + idx).getBytes(StandardCharsets.UTF_8)).toString();
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("docId", docId);
                payload.put("moduleId", moduleId);
                payload.put("docTitle", title == null ? "" : title);
                payload.put("docTags", tags == null ? "" : tags);
                payload.put("chunkIndex", idx);
                payload.put("content", c);
                points.add(new QdrantService.QdrantPoint(pointId, vec, payload));

                DocChunk dc = new DocChunk();
                dc.setDocId(docId);
                dc.setModuleId(moduleId);
                dc.setChunkIndex(idx);
                dc.setContent(c);
                dc.setEmbeddingVersion(embeddingVersion());
                entities.add(dc);
                idx++;
            }

            // 4. 写入向量库与切片表
            if (!points.isEmpty()) {
                qdrantService.upsert(points);
                docChunkRepository.saveAll(entities);
            }

            // 5. 标记文档已索引
            final String version = embeddingVersion();
            docInfoRepository.findByDocId(docId).ifPresent(d -> {
                d.setIndexed(true);
                d.setEmbeddingVersion(version);
                docInfoRepository.save(d);
            });

            log.info("文档 {} 索引完成：共 {} 个切片", docId, points.size());
        } catch (Exception e) {
            log.warn("文档 {} 向量索引失败（不影响文档保存）: {}", doc.getDocId(), e.getMessage());
        }
    }

    /** 删除文档的全部索引（文档删除时调用） */
    public void removeDocument(String docId) {
        try {
            docChunkRepository.deleteByDocId(docId);
            qdrantService.deleteByDocId(docId);
            log.info("文档 {} 索引已删除", docId);
        } catch (Exception e) {
            log.warn("删除文档 {} 索引失败（可忽略）: {}", docId, e.getMessage());
        }
    }

    /**
     * 全量重建索引（模型升级或首次迁移时使用）。
     * 仅处理有正文内容的文档。
     */
    public void rebuildAll() {
        List<DocInfo> docs = docInfoRepository.findAll().stream()
                .filter(d -> d.getDocContent() != null && !d.getDocContent().isBlank())
                .collect(Collectors.toList());
        log.info("开始全量重建索引，待处理文档 {} 篇", docs.size());
        for (DocInfo d : docs) {
            indexDocument(d);
        }
        log.info("全量重建索引完成");
    }
}
