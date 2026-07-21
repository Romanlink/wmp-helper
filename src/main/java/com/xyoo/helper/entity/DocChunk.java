package com.xyoo.helper.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Comment;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档切片实体（RAG 检索增强）。
 * <p>
 * 仅持久化切片原文与元数据，向量本身存储在 Qdrant 中（point_id 由 chunk 的稳定主键派生）。
 * 本表用于「按文档删除/重建索引」时定位与人工排查。
 * </p>
 */
@Entity
@Table(name = "doc_chunk")
public class DocChunk implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "doc_id", nullable = false, length = 32)
    @Comment("所属文档业务ID，关联 doc_info.doc_id")
    private String docId;

    @Column(name = "module_id", nullable = false)
    @Comment("所属菜单ID，关联 sys_module.id（RAG 检索 RBAC 过滤用）")
    private Long moduleId;

    @Column(name = "chunk_index", nullable = false)
    @Comment("切片序号，从0开始")
    private int chunkIndex;

    @Column(name = "content", columnDefinition = "TEXT")
    @Comment("切片原文（Markdown 片段）")
    private String content;

    @Column(name = "embedding_version", length = 64)
    @Comment("索引所用 embedding 模型版本")
    private String embeddingVersion;

    @Column(name = "create_time", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEmbeddingVersion() {
        return embeddingVersion;
    }

    public void setEmbeddingVersion(String embeddingVersion) {
        this.embeddingVersion = embeddingVersion;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
