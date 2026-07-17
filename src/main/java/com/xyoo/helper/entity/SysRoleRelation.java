package com.xyoo.helper.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * 角色-菜单关联实体（多对多中间表）
 */
@Entity
@Table(name = "sys_role_relation")
@IdClass(SysRoleRelation.PK.class)
public class SysRoleRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "module_id")
    private Long moduleId;

    public SysRoleRelation() {}

    public SysRoleRelation(Long roleId, Long moduleId) {
        this.roleId = roleId;
        this.moduleId = moduleId;
    }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public Long getModuleId() { return moduleId; }
    public void setModuleId(Long moduleId) { this.moduleId = moduleId; }

    /** 复合主键类 */
    public static class PK implements Serializable {
        private Long roleId;
        private Long moduleId;

        public PK() {}

        public PK(Long roleId, Long moduleId) {
            this.roleId = roleId;
            this.moduleId = moduleId;
        }

        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }

        public Long getModuleId() { return moduleId; }
        public void setModuleId(Long moduleId) { this.moduleId = moduleId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PK pk = (PK) o;
            return roleId.equals(pk.roleId) && moduleId.equals(pk.moduleId);
        }

        @Override
        public int hashCode() {
            return roleId.hashCode() * 31 + moduleId.hashCode();
        }
    }
}
