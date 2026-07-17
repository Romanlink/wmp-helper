package com.xyoo.helper.repository;

import com.xyoo.helper.entity.SysModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 系统菜单数据访问层
 */
@Repository
public interface SysModuleRepository extends JpaRepository<SysModule, Long>, JpaSpecificationExecutor<SysModule> {

    /**
     * 查询所有展示的菜单，按排序序号升序
     *
     * @param isVisible 是否展示
     * @return 菜单列表
     */
    List<SysModule> findAllByIsVisibleOrderBySortOrderAsc(Boolean isVisible);

    /**
     * 模糊搜索菜单名称（包含关键词），仅查询展示的菜单，按排序序号升序
     *
     * @param moduleName  菜单名称关键词
     * @param isVisible 是否展示
     * @return 匹配的菜单列表
     */
    List<SysModule> findByModuleNameContainingAndIsVisibleOrderBySortOrderAsc(String moduleName, Boolean isVisible);

    /**
     * 根据父菜单ID查询展示的子菜单列表，按排序序号升序
     *
     * @param parentId  父菜单ID
     * @param isVisible 是否展示
     * @return 子菜单列表
     */
    List<SysModule> findByParentIdAndIsVisibleOrderBySortOrderAsc(Long parentId, Boolean isVisible);

    /**
     * 根据父菜单ID查询所有子菜单（含隐藏），按排序序号升序
     * 用于级联删除场景
     *
     * @param parentId 父菜单ID
     * @return 子菜单列表（含隐藏的）
     */
    List<SysModule> findByParentIdOrderBySortOrderAsc(Long parentId);

    /**
     * 查询展示的菜单且ID在允许集合内（RBAC 过滤），按排序序号升序
     *
     * @param isVisible 是否展示
     * @param ids       允许的菜单ID集合（角色可见菜单）
     */
    List<SysModule> findAllByIsVisibleAndIdInOrderBySortOrderAsc(Boolean isVisible, Collection<Long> ids);

    /**
     * 模糊搜索菜单名称，仅查询展示且ID在允许集合内的菜单（RBAC 过滤），按排序序号升序
     *
     * @param moduleName  菜单名称关键词
     * @param isVisible 是否展示
     * @param ids       允许的菜单ID集合（角色可见菜单）
     */
    List<SysModule> findByModuleNameContainingAndIsVisibleAndIdInOrderBySortOrderAsc(String moduleName,
                                                                                  Boolean isVisible,
                                                                                  Collection<Long> ids);

    /**
     * 根据父菜单ID查询展示且ID在允许集合内的子菜单（RBAC 过滤），按排序序号升序
     *
     * @param parentId  父菜单ID
     * @param isVisible 是否展示
     * @param ids       允许的菜单ID集合（角色可见菜单）
     */
    List<SysModule> findByParentIdAndIsVisibleAndIdInOrderBySortOrderAsc(Long parentId,
                                                                        Boolean isVisible,
                                                                        Collection<Long> ids);

    /**
     * 统计指定父菜单下的子菜单数量
     *
     * @param parentId 父菜单ID
     * @return 子菜单数量
     */
    long countByParentId(Long parentId);
}
