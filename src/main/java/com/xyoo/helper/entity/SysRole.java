package com.xyoo.helper.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import org.hibernate.annotations.Comment;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统角色实体
 */
@Entity
@Table(name = "sys_role", indexes = {
        @Index(name = "uk_role_code", columnList = "role_code", unique = true)
})
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("角色ID，主键")
    private Long id;

    @Column(name = "role_name", nullable = false, length = 64)
    @Comment("角色名称")
    private String roleName;

    @Column(name = "role_code", nullable = false, length = 64, unique = true)
    @Comment("角色编码（唯一）")
    private String roleCode;

    @Column(name = "description", length = 256)
    @Comment("角色描述")
    private String description = "";

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "create_time", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (this.createTime == null) this.createTime = LocalDateTime.now();
    }

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
