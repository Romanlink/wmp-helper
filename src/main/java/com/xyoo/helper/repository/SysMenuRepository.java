package com.xyoo.helper.repository;

import com.xyoo.helper.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 系统菜单数据访问层
 */
@Repository
public interface SysMenuRepository extends JpaRepository<SysMenu, Long>, JpaSpecificationExecutor<SysMenu> {

    /**
     * 查询所有展示的菜单，按排序序号升序
     *
     * @param isVisible 是否展示
     * @return 菜单列表
     */
    List<SysMenu> findAllByIsVisibleOrderBySortOrderAsc(Boolean isVisible);

    /**
     * 模糊搜索菜单名称（包含关键词），仅查询展示的菜单，按排序序号升序
     *
     * @param menuName  菜单名称关键词
     * @param isVisible 是否展示
     * @return 匹配的菜单列表
     */
    List<SysMenu> findByMenuNameContainingAndIsVisibleOrderBySortOrderAsc(String menuName, Boolean isVisible);

    /**
     * 根据父菜单ID查询展示的子菜单列表，按排序序号升序
     *
     * @param parentId  父菜单ID
     * @param isVisible 是否展示
     * @return 子菜单列表
     */
    List<SysMenu> findByParentIdAndIsVisibleOrderBySortOrderAsc(Long parentId, Boolean isVisible);

    /**
     * 根据父菜单ID查询所有子菜单（含隐藏），按排序序号升序
     * 用于级联删除场景
     *
     * @param parentId 父菜单ID
     * @return 子菜单列表（含隐藏的）
     */
    List<SysMenu> findByParentIdOrderBySortOrderAsc(Long parentId);

    /**
     * 统计指定父菜单下的子菜单数量
     *
     * @param parentId 父菜单ID
     * @return 子菜单数量
     */
    long countByParentId(Long parentId);
}
