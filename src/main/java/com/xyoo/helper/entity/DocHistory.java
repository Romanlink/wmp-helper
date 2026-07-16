package com.xyoo.helper.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import org.hibernate.annotations.Comment;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档编辑历史实体（拉链表）
 * <p>
 * 记录每次文档编辑操作的完整快照，包括标题、标签、内容等。
 * 支持版本追溯和历史回滚。
 * </p>
 */
@Entity
@Table(name = "doc_history")
public class DocHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "doc_info_id", nullable = false)
    @Comment("关联文档ID，关联 doc_info.id")
    private Long docInfoId;

    @Column(name = "doc_title", nullable = false, length = 256)
    @Comment("编辑时的文档标题快照")
    private String docTitle;

    @Column(name = "doc_tags", length = 512)
    @Comment("编辑时的标签快照")
    private String docTags;

    @Column(name = "doc_content", columnDefinition = "TEXT")
    @Comment("编辑时的内容快照")
    private String docContent;

    @Column(name = "change_summary", length = 512)
    @Comment("本次变更摘要说明")
    private String changeSummary;

    @Column(name = "operation_type", nullable = false, length = 32)
    @Comment("操作类型：CREATE 新建 / UPDATE 编辑")
    private String operationType;

    @Column(name = "operator", length = 64)
    @Comment("操作人")
    private String operator;

    @Column(name = "version", nullable = false)
    @Comment("版本号，从1开始递增")
    private Integer version = 1;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "operate_time", nullable = false)
    @Comment("操作时间")
    private LocalDateTime operateTime;

    @PrePersist
    protected void onCreate() {
        this.operateTime = LocalDateTime.now();
        if (this.version == null) {
            this.version = 1;
        }
    }

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocInfoId() {
        return docInfoId;
    }

    public void setDocInfoId(Long docInfoId) {
        this.docInfoId = docInfoId;
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

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getOperateTime() {
        return operateTime;
    }

    public void setOperateTime(LocalDateTime operateTime) {
        this.operateTime = operateTime;
    }
}
