package com.xyoo.helper.repository;

import com.xyoo.helper.entity.SysParam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 系统参数数据访问层
 */
@Repository
public interface SysParamRepository extends JpaRepository<SysParam, String> {
}
