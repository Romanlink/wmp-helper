package com.xyoo.helper.repository;

import com.xyoo.helper.entity.SysRoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysRoleMenuRepository extends JpaRepository<SysRoleMenu, SysRoleMenu.PK> {

    List<SysRoleMenu> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);
}
