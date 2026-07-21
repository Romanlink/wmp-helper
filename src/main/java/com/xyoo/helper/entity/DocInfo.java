package com.xyoo.helper.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Comment;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档信息实体
 * <p>
 * 文档归属于某个菜单，支持 Markdown 内容和标签。
 * 原始文件路径指向本地 PDF 文件，可通过下载接口获取。
 * </p>
 */
@Entity
@Table(name = "doc_info")
public class DocInfo implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;
    
    @Column(name = "doc_id", nullable = false, unique = true, length = 32)
    @Comment("文档业务ID（UUID）")
    private String docId;

    @Column(name = "original_path", length = 512)
    @Comment("原始文件路径（PDF等本地文件）")
    private String originalPath;

    @Column(name = "attach_pwd", length = 256)
    @Comment("附件加密密码，仅写入不返回")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String attachPwd;

    @NotNull(message = "所属菜单不能为空")
    @Column(name = "module_id", nullable = false)
    @Comment("所属菜单ID，关联 sys_module.id")
    private Long moduleId;

    @NotBlank(message = "文档标题不能为空")
    @Column(name = "doc_title", nullable = false, length = 256)
    @Comment("文档标题")
    private String docTitle;

    @Column(name = "doc_tags", length = 512)
    @Comment("文档标签，多个用逗号分隔")
    private String docTags;

    @Column(name = "doc_content", columnDefinition = "TEXT")
    @Comment("文档内容（Markdown 语法）")
    private String docContent;

    @Column(name = "is_visible", nullable = false)
    @Comment("是否展示：0=隐藏，1=展示")
    private Boolean isVisible = true;

    @Column(name = "indexed", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    @Comment("是否已建立向量索引：0=未索引，1=已索引")
    private Boolean indexed = false;

    @Column(name = "embedding_version", length = 64)
    @Comment("索引使用的 embedding 模型版本，便于模型升级后全量重建")
    private String embeddingVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "create_time", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "update_time", nullable = false)
    @Comment("更新时间")
    private LocalDateTime updateTime;

    @Transient
    @Comment("所属菜单名称（关联查询填充，不持久化）")
    private String moduleName;

    // ==================== JPA 生命周期回调 ====================

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        if (this.isVisible == null) {
            this.isVisible = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }

    // ==================== Getters & Setters ====================

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

    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    public String getAttachPwd() {
        return attachPwd;
    }

    public void setAttachPwd(String attachPwd) {
        this.attachPwd = attachPwd;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getDocTitle() {
        return docTitle;
    }

    public void setDocTitle(String docTitle) {
        this.docTitle = docTitle;
    }

    public String getDocTags() {
        return docTags;
    }

    public void setDocTags(String docTags) {
        this.docTags = docTags;
    }

    public String getDocContent() {
        return docContent;
    }

    public void setDocContent(String docContent) {
        this.docContent = docContent;
    }

    public Boolean getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(Boolean isVisible) {
        this.isVisible = isVisible;
    }

    public Boolean getIndexed() {
        return indexed;
    }

    public void setIndexed(Boolean indexed) {
        this.indexed = indexed;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
}
