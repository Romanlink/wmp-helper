package com.xyoo.helper.repository;

import com.xyoo.helper.entity.SysRoleRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysRoleRelationRepository extends JpaRepository<SysRoleRelation, SysRoleRelation.PK> {

    List<SysRoleRelation> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);
}
