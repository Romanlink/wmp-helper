package com.xyoo.helper.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Comment;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统菜单实体
 * <p>
 * 支持多级菜单（父子关系），通过 parentId 自关联。
 * 菜单名称要求 4-6 个中文汉字。
 * </p>
 */
@Entity
@Table(name = "sys_menu", indexes = {
        @Index(name = "idx_parent_id", columnList = "parent_id"),
        @Index(name = "idx_is_visible", columnList = "is_visible"),
        @Index(name = "idx_sort_order", columnList = "sort_order")
})
public class SysMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("菜单ID，主键")
    private Long id;

    /** 父菜单ID，0 表示顶级菜单 */
    @Column(name = "parent_id", nullable = false)
    @Comment("父菜单ID，0表示顶级菜单")
    private Long parentId = 0L;

    /** 菜单名称，4-6 个中文汉字 */
    @NotBlank(message = "菜单名称不能为空")
    @Size(min = 4, max = 6, message = "菜单名称必须为4-6个字符")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5]+$", message = "菜单名称必须为中文汉字")
    @Column(name = "menu_name", nullable = false, length = 32)
    @Comment("菜单名称，4-6个中文汉字")
    private String menuName;

    /** 菜单路由路径 */
    @Column(name = "menu_path", length = 128)
    @Comment("菜单路由路径/URL")
    private String menuPath = "";

    /** 菜单图标 */
    @Column(name = "menu_icon", length = 64)
    @Comment("菜单图标")
    private String menuIcon = "";

    /** 排序序号，数值越小越靠前 */
    @Column(name = "sort_order", nullable = false)
    @Comment("排序序号，数值越小越靠前")
    private Integer sortOrder = 0;

    /**
     * 是否展示
     * <p>0 = 隐藏，1 = 展示。列表查询和模糊搜索均只返回展示的菜单。</p>
     */
    @Column(name = "is_visible", nullable = false)
    @Comment("是否展示：0=隐藏，1=展示")
    private Boolean isVisible = true;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "create_time", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "update_time", nullable = false)
    @Comment("更新时间")
    private LocalDateTime updateTime;

    // ==================== 生命周期回调 ====================

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createTime == null) {
            this.createTime = now;
        }
        if (this.updateTime == null) {
            this.updateTime = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }

    // ==================== getter / setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getMenuPath() {
        return menuPath;
    }

    public void setMenuPath(String menuPath) {
        this.menuPath = menuPath;
    }

    public String getMenuIcon() {
        return menuIcon;
    }

    public void setMenuIcon(String menuIcon) {
        this.menuIcon = menuIcon;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(Boolean isVisible) {
        this.isVisible = isVisible;
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

    @Override
    public String toString() {
        return "SysMenu{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", menuName='" + menuName + '\'' +
                ", menuPath='" + menuPath + '\'' +
                ", sortOrder=" + sortOrder +
                ", isVisible=" + isVisible +
                '}';
    }
}
