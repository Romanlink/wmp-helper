package com.xyoo.helper.controller;

import com.xyoo.helper.common.Result;
import com.xyoo.helper.entity.SysMenu;
import com.xyoo.helper.service.SysMenuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 系统菜单 REST API 控制器
 * <p>
 * 基础路径：/api/menus
 * </p>
 */
@RestController
@RequestMapping("/api/menus")
public class SysMenuController {

    private final SysMenuService menuService;

    public SysMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    // ==================== 查询 ====================

    /**
     * 查询所有展示的菜单列表
     * <p>仅返回 isVisible=true 的菜单，按 sortOrder 升序。</p>
     *
     * <pre>GET /api/menus</pre>
     */
    @GetMapping
    public Result<List<SysMenu>> list() {
        List<SysMenu> menus = menuService.listVisibleMenus();
        return Result.success(menus);
    }

    /**
     * 模糊搜索菜单（按名称）
     * <p>仅搜索 isVisible=true 的菜单。</p>
     *
     * <pre>GET /api/menus/search?keyword=管理</pre>
     */
    @GetMapping("/search")
    public Result<List<SysMenu>> search(@RequestParam(required = false) String keyword) {
        List<SysMenu> menus = menuService.searchMenus(keyword);
        return Result.success(menus);
    }

    /**
     * 根据父菜单ID查询子菜单列表
     * <p>仅返回 isVisible=true 的子菜单。</p>
     *
     * <pre>GET /api/menus/children?parentId=1</pre>
     */
    @GetMapping("/children")
    public Result<List<SysMenu>> children(@RequestParam(defaultValue = "0") Long parentId) {
        List<SysMenu> menus = menuService.getChildMenus(parentId);
        return Result.success(menus);
    }

    /**
     * 查询菜单详情
     *
     * <pre>GET /api/menus/{id}</pre>
     */
    @GetMapping("/{id}")
    public Result<SysMenu> detail(@PathVariable Long id) {
        Optional<SysMenu> menu = menuService.getById(id);
        return menu.map(Result::success)
                .orElseGet(() -> Result.error(404, "菜单不存在"));
    }

    // ==================== 新增 ====================

    /**
     * 新增菜单
     *
     * <pre>POST /api/menus
     * Body:
     * {
     *     "parentId": 0,
     *     "menuName": "订单管理",
     *     "menuPath": "/order",
     *     "menuIcon": "el-icon-s-order",
     *     "sortOrder": 1,
     *     "isVisible": true
     * }
     * </pre>
     */
    @PostMapping
    public Result<SysMenu> create(@Valid @RequestBody SysMenu menu) {
        SysMenu saved = menuService.create(menu);
        return Result.success("创建成功", saved);
    }

    // ==================== 修改 ====================

    /**
     * 修改菜单
     *
     * <pre>PUT /api/menus/{id}</pre>
     */
    @PutMapping("/{id}")
    public Result<SysMenu> update(@PathVariable Long id, @Valid @RequestBody SysMenu menu) {
        try {
            SysMenu updated = menuService.update(id, menu);
            return Result.success("修改成功", updated);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================== 删除 ====================

    /**
     * 删除菜单（级联删除子菜单）
     *
     * <pre>DELETE /api/menus/{id}</pre>
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            menuService.delete(id);
            return Result.success("删除成功", null);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
