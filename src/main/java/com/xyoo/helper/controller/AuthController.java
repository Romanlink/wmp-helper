package com.xyoo.helper.controller;

import com.xyoo.helper.common.Result;
import com.xyoo.helper.entity.SysMenu;
import com.xyoo.helper.entity.SysUser;
import com.xyoo.helper.service.AuthService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 认证控制器 — 登录、登出、获取当前用户信息
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录
     * <p>
     * 请求体: { "username": "admin", "password": "admin123" }
     * </p>
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.trim().isEmpty()) {
            return Result.error(400, "用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return Result.error(400, "密码不能为空");
        }

        AuthService.LoginResult result = authService.login(username.trim(), password);

        Map<String, Object> data = new HashMap<>();
        data.put("token", result.getToken());
        data.putAll(result.getUserInfo());

        return Result.success("登录成功", data);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            authService.logout(token);
        }
        return Result.success();
    }

    /**
     * 获取当前登录用户信息 + 可访问菜单
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return Result.error(401, "未登录");
        }

        Optional<SysUser> userOpt = authService.getCurrentUser(token);
        if (!userOpt.isPresent()) {
            return Result.error(401, "用户不存在");
        }

        SysUser user = userOpt.get();
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("roleId", user.getRoleId());
        data.put("status", user.getStatus());

        // 查询角色名称
        // 查询可访问菜单
        List<SysMenu> menus = authService.getMenusByUserRole(user.getRoleId());
        List<Map<String, Object>> menuList = new ArrayList<>();
        for (SysMenu menu : menus) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", menu.getId());
            m.put("menuName", menu.getMenuName());
            m.put("menuPath", menu.getMenuPath());
            m.put("parentId", menu.getParentId());
            m.put("sortOrder", menu.getSortOrder());
            menuList.add(m);
        }
        data.put("menus", menuList);

        return Result.success(data);
    }

    /**
     * 从请求头中提取 Token
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
