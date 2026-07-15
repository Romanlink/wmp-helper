package com.xyoo.helper.service;

import com.xyoo.helper.entity.SysMenu;
import com.xyoo.helper.repository.SysMenuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 系统菜单业务逻辑层
 * <p>
 * 列表查询和模糊搜索均只返回 isVisible=true 的菜单。
 * </p>
 */
@Service
public class SysMenuService {

    private final SysMenuRepository menuRepository;

    public SysMenuService(SysMenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    // ==================== 查询 ====================

    /**
     * 查询所有展示的菜单列表（仅 isVisible=true）
     */
    @Transactional(readOnly = true)
    public List<SysMenu> listVisibleMenus() {
        return menuRepository.findAllByIsVisibleOrderBySortOrderAsc(true);
    }

    /**
     * 模糊搜索菜单（仅 isVisible=true）
     *
     * @param keyword 搜索关键词
     */
    @Transactional(readOnly = true)
    public List<SysMenu> searchMenus(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return listVisibleMenus();
        }
        return menuRepository.findByMenuNameContainingAndIsVisibleOrderBySortOrderAsc(keyword.trim(), true);
    }

    /**
     * 根据父菜单ID查询展示的子菜单列表
     *
     * @param parentId 父菜单ID，0 表示顶级菜单
     */
    @Transactional(readOnly = true)
    public List<SysMenu> getChildMenus(Long parentId) {
        if (parentId == null) {
            parentId = 0L;
        }
        return menuRepository.findByParentIdAndIsVisibleOrderBySortOrderAsc(parentId, true);
    }

    /**
     * 根据ID查询菜单详情
     */
    @Transactional(readOnly = true)
    public Optional<SysMenu> getById(Long id) {
        return menuRepository.findById(id);
    }

    // ==================== 新增 ====================

    /**
     * 新增菜单
     */
    @Transactional
    public SysMenu create(SysMenu menu) {
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
    public SysMenu update(Long id, SysMenu menu) {
        SysMenu existing = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("菜单不存在，id=" + id));

        if (menu.getParentId() != null) {
            // 防止将父菜单设为自己的子菜单（循环依赖）
            if (menu.getParentId().equals(id)) {
                throw new IllegalArgumentException("父菜单不能是自身");
            }
            existing.setParentId(menu.getParentId());
        }
        if (menu.getMenuName() != null && !menu.getMenuName().trim().isEmpty()) {
            existing.setMenuName(menu.getMenuName().trim());
        }
        if (menu.getMenuPath() != null) {
            existing.setMenuPath(menu.getMenuPath());
        }
        if (menu.getMenuIcon() != null) {
            existing.setMenuIcon(menu.getMenuIcon());
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
        List<SysMenu> children = menuRepository.findByParentIdOrderBySortOrderAsc(parentId);
        for (SysMenu child : children) {
            deleteChildren(child.getId());
            menuRepository.deleteById(child.getId());
        }
    }
}
