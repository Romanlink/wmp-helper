package com.xyoo.helper.entity;

import javax.persistence.*;
import java.io.Serializable;

/**
 * 角色-菜单关联实体（多对多中间表）
 */
@Entity
@Table(name = "sys_role_menu")
@IdClass(SysRoleMenu.PK.class)
public class SysRoleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "menu_id")
    private Long menuId;

    public SysRoleMenu() {}

    public SysRoleMenu(Long roleId, Long menuId) {
        this.roleId = roleId;
        this.menuId = menuId;
    }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }

    /** 复合主键类 */
    public static class PK implements Serializable {
        private Long roleId;
        private Long menuId;

        public PK() {}

        public PK(Long roleId, Long menuId) {
            this.roleId = roleId;
            this.menuId = menuId;
        }

        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }

        public Long getMenuId() { return menuId; }
        public void setMenuId(Long menuId) { this.menuId = menuId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PK pk = (PK) o;
            return roleId.equals(pk.roleId) && menuId.equals(pk.menuId);
        }

        @Override
        public int hashCode() {
            return roleId.hashCode() * 31 + menuId.hashCode();
        }
    }
}
