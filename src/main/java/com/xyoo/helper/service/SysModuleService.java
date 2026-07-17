package com.xyoo.helper.service;

import com.xyoo.helper.entity.SysModule;
import com.xyoo.helper.repository.SysModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 系统菜单业务逻辑层
 * <p>
 * 列表查询和模糊搜索均只返回 isVisible=true 的菜单。
 * </p>
 */
@Service
public class SysModuleService {

    private final SysModuleRepository menuRepository;

    public SysModuleService(SysModuleRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    // ==================== 查询 ====================

    /**
     * 查询所有展示的菜单列表（仅 isVisible=true）
     */
    @Transactional(readOnly = true)
    public List<SysModule> listVisibleMenus() {
        return menuRepository.findAllByIsVisibleOrderBySortOrderAsc(true);
    }

    /**
     * 查询当前角色可见的展示菜单列表（RBAC 过滤）
     * <p>allowedModuleIds 为当前登录人角色可访问的菜单ID集合；为空表示无权限，返回空列表。</p>
     *
     * @param allowedModuleIds 角色可见菜单ID集合
     */
    @Transactional(readOnly = true)
    public List<SysModule> listVisibleMenus(Set<Long> allowedModuleIds) {
        if (allowedModuleIds == null || allowedModuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return menuRepository.findAllByIsVisibleAndIdInOrderBySortOrderAsc(true, allowedModuleIds);
    }

    /**
     * 模糊搜索菜单（仅 isVisible=true）
     *
     * @param keyword 搜索关键词
     */
    @Transactional(readOnly = true)
    public List<SysModule> searchMenus(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return listVisibleMenus();
        }
        return menuRepository.findByModuleNameContainingAndIsVisibleOrderBySortOrderAsc(keyword.trim(), true);
    }

    /**
     * 模糊搜索当前角色可见的菜单（RBAC 过滤）
     *
     * @param keyword        搜索关键词
     * @param allowedModuleIds 角色可见菜单ID集合；为空表示无权限，返回空列表
     */
    @Transactional(readOnly = true)
    public List<SysModule> searchMenus(String keyword, Set<Long> allowedModuleIds) {
        if (allowedModuleIds == null || allowedModuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            return listVisibleMenus(allowedModuleIds);
        }
        return menuRepository.findByModuleNameContainingAndIsVisibleAndIdInOrderBySortOrderAsc(
                keyword.trim(), true, allowedModuleIds);
    }

    /**
     * 根据父菜单ID查询展示的子菜单列表
     *
     * @param parentId 父菜单ID，0 表示顶级菜单
     */
    @Transactional(readOnly = true)
    public List<SysModule> getChildMenus(Long parentId) {
        if (parentId == null) {
            parentId = 0L;
        }
        return menuRepository.findByParentIdAndIsVisibleOrderBySortOrderAsc(parentId, true);
    }

    /**
     * 根据父菜单ID查询当前角色可见的展示子菜单列表（RBAC 过滤）
     *
     * @param parentId       父菜单ID，0 表示顶级菜单
     * @param allowedModuleIds 角色可见菜单ID集合；为空表示无权限，返回空列表
     */
    @Transactional(readOnly = true)
    public List<SysModule> getChildMenus(Long parentId, Set<Long> allowedModuleIds) {
        if (allowedModuleIds == null || allowedModuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (parentId == null) {
            parentId = 0L;
        }
        return menuRepository.findByParentIdAndIsVisibleAndIdInOrderBySortOrderAsc(parentId, true, allowedModuleIds);
    }

    /**
     * 根据ID查询菜单详情
     */
    @Transactional(readOnly = true)
    public Optional<SysModule> getById(Long id) {
        return menuRepository.findById(id);
    }

    // ==================== 新增 ====================

    /**
     * 新增菜单
     */
    @Transactional
    public SysModule create(SysModule menu) {
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getSortOrder() == null) {
            menu.setSortOrder(0);
        }
        if (menu.getIsVisible() == null) {
            menu.setIsVisible(true);
        }
        return menuRepository.save(menu);
    }

    // ==================== 修改 ====================

    /**
     * 修改菜单
     */
    @Transactional
    public SysModule update(Long id, SysModule menu) {
        SysModule existing = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("菜单不存在，id=" + id));

        if (menu.getParentId() != null) {
            // 防止将父菜单设为自己的子菜单（循环依赖）
            if (menu.getParentId().equals(id)) {
                throw new IllegalArgumentException("父菜单不能是自身");
            }
            existing.setParentId(menu.getParentId());
        }
        if (menu.getModuleName() != null && !menu.getModuleName().trim().isEmpty()) {
            existing.setModuleName(menu.getModuleName().trim());
        }
        if (menu.getModulePath() != null) {
            existing.setModulePath(menu.getModulePath());
        }
        if (menu.getModuleIcon() != null) {
            existing.setModuleIcon(menu.getModuleIcon());
        }
        if (menu.getSortOrder() != null) {
            existing.setSortOrder(menu.getSortOrder());
        }
        if (menu.getIsVisible() != null) {
            existing.setIsVisible(menu.getIsVisible());
        }

        return menuRepository.save(existing);
    }

    // ==================== 删除 ====================

    /**
     * 删除菜单（同时删除所有子菜单）
     */
    @Transactional
    public void delete(Long id) {
        if (!menuRepository.existsById(id)) {
            throw new RuntimeException("菜单不存在，id=" + id);
        }
        // 递归删除子菜单
        deleteChildren(id);
        menuRepository.deleteById(id);
    }

    /**
     * 递归删除子菜单（包含隐藏的）
     */
    private void deleteChildren(Long parentId) {
        List<SysModule> children = menuRepository.findByParentIdOrderBySortOrderAsc(parentId);
        for (SysModule child : children) {
            deleteChildren(child.getId());
            menuRepository.deleteById(child.getId());
        }
    }
}
