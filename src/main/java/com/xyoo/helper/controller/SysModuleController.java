package com.xyoo.helper.controller;

import com.xyoo.helper.common.BaseController;
import com.xyoo.helper.common.LoginUser;
import com.xyoo.helper.common.Result;
import com.xyoo.helper.entity.SysModule;
import com.xyoo.helper.service.AuthService;
import com.xyoo.helper.service.SysModuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 系统菜单 REST API 控制器
 * <p>
 * 基础路径：/api/modules
 * </p>
 */
@RestController
@RequestMapping("/api/modules")
public class SysModuleController extends BaseController {

    private final SysModuleService menuService;
    private final AuthService authService;

    public SysModuleController(SysModuleService menuService, AuthService authService) {
        this.menuService = menuService;
        this.authService = authService;
    }

    // ==================== 查询 ====================

    /**
     * 查询当前登录人角色可见的菜单列表
     * <p>仅返回 isVisible=true 且属于当前角色可访问的菜单，按 sortOrder 升序。</p>
     *
     * <pre>GET /api/modules</pre>
     */
    @GetMapping
    public Result<List<SysModule>> list() {
        Set<Long> allowed = resolveAllowedModuleIds();
        List<SysModule> menus = menuService.listVisibleMenus(allowed);
        return Result.success(menus);
    }

    /**
     * 模糊搜索当前登录人角色可见的菜单（按名称）
     * <p>仅搜索 isVisible=true 且属于当前角色可访问的菜单。</p>
     *
     * <pre>GET /api/modules/search?keyword=管理</pre>
     */
    @GetMapping("/search")
    public Result<List<SysModule>> search(@RequestParam(required = false) String keyword) {
        Set<Long> allowed = resolveAllowedModuleIds();
        List<SysModule> menus = menuService.searchMenus(keyword, allowed);
        return Result.success(menus);
    }

    /**
     * 根据父菜单ID查询当前登录人角色可见的子菜单列表
     * <p>仅返回 isVisible=true 且属于当前角色可访问的子菜单。</p>
     *
     * <pre>GET /api/modules/children?parentId=1</pre>
     */
    @GetMapping("/children")
    public Result<List<SysModule>> children(@RequestParam(defaultValue = "0") Long parentId) {
        Set<Long> allowed = resolveAllowedModuleIds();
        List<SysModule> menus = menuService.getChildMenus(parentId, allowed);
        return Result.success(menus);
    }

    /**
     * 解析当前登录人角色可访问的菜单ID集合。
     * <p>未登录、无角色或上下文缺失时返回空集，对应「无可见菜单」。</p>
     */
    private Set<Long> resolveAllowedModuleIds() {
        LoginUser cur = getCurInfo();
        if (cur == null || cur.getRoleId() == null) {
            return Collections.emptySet();
        }
        return authService.findModuleIdsByRole(cur.getRoleId());
    }

    /**
     * 查询菜单详情
     *
     * <pre>GET /api/modules/{id}</pre>
     */
    @GetMapping("/{id}")
    public Result<SysModule> detail(@PathVariable Long id) {
        Optional<SysModule> menu = menuService.getById(id);
        return menu.map(Result::success)
                .orElseGet(() -> Result.error(404, "菜单不存在"));
    }

    // ==================== 新增 ====================

    /**
     * 新增菜单
     *
     * <pre>POST /api/modules
     * Body:
     * {
     *     "parentId": 0,
     *     "moduleName": "订单管理",
     *     "modulePath": "/order",
     *     "moduleIcon": "el-icon-s-order",
     *     "sortOrder": 1,
     *     "isVisible": true
     * }
     * </pre>
     */
    @PostMapping
    public Result<SysModule> create(@Valid @RequestBody SysModule menu) {
        SysModule saved = menuService.create(menu);
        return Result.success("创建成功", saved);
    }

    // ==================== 修改 ====================

    /**
     * 修改菜单
     *
     * <pre>PUT /api/modules/{id}</pre>
     */
    @PutMapping("/{id}")
    public Result<SysModule> update(@PathVariable Long id, @Valid @RequestBody SysModule menu) {
        try {
            SysModule updated = menuService.update(id, menu);
            return Result.success("修改成功", updated);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================== 删除 ====================

    /**
     * 删除菜单（级联删除子菜单）
     *
     * <pre>DELETE /api/modules/{id}</pre>
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
