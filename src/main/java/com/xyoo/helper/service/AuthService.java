package com.xyoo.helper.service;

import com.xyoo.helper.entity.SysModule;
import com.xyoo.helper.entity.SysRole;
import com.xyoo.helper.entity.SysRoleRelation;
import com.xyoo.helper.common.LoginUser;
import com.xyoo.helper.entity.SysUser;
import com.xyoo.helper.repository.SysModuleRepository;
import com.xyoo.helper.repository.SysRoleRelationRepository;
import com.xyoo.helper.repository.SysRoleRepository;
import com.xyoo.helper.repository.SysUserRepository;
import com.xyoo.helper.util.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 认证服务 — 登录、登出、Token 校验
 * <p>
 * 使用 Redis 实现单点登录（同一用户只保留最新 token）：
 * <ul>
 *   <li>key: <code>auth:user:{userId}</code> → value: token</li>
 *   <li>当新登录覆盖旧 token 后，旧 token 在 Redis 中不存在 → 下次请求返回 401</li>
 * </ul>
 * </p>
 */
@Service
public class AuthService {

    private static final String REDIS_KEY_PREFIX = "auth:user:";
    private static final long TOKEN_TTL_TIMES = 5;

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysRoleRelationRepository roleMenuRepository;
    private final SysModuleRepository menuRepository;
    private final StringRedisTemplate redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(SysUserRepository userRepository,
                       SysRoleRepository roleRepository,
                       SysRoleRelationRepository roleMenuRepository,
                       SysModuleRepository menuRepository,
                       StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleMenuRepository = roleMenuRepository;
        this.menuRepository = menuRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 登录验证
     *
     * @param username 用户名
     * @param password 明文密码
     * @return LoginResult 包含 token 和用户信息
     */
    public LoginResult login(String username, String password) {
        // 1. 查找用户
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        // 2. 检查账号状态
        if (user.getStatus() != null && !user.getStatus()) {
            throw new RuntimeException("该账号已被禁用");
        }

        // 3. 校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 4. 生成 Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());

        // 5. 存入 Redis（覆盖旧 token，实现单点登录）
        String redisKey = REDIS_KEY_PREFIX + user.getId();
        redisTemplate.opsForValue().set(redisKey, token, TOKEN_TTL_TIMES, TimeUnit.HOURS);

        // 6. 查询角色信息
        String roleName = "";
        String roleCode = "";
        if (user.getRoleId() != null) {
            Optional<SysRole> role = roleRepository.findById(user.getRoleId());
            if (role.isPresent()) {
                roleName = role.get().getRoleName();
                roleCode = role.get().getRoleCode();
            }
        }

        // 7. 组装返回数据
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("roleId", user.getRoleId());
        userInfo.put("roleName", roleName);
        userInfo.put("roleCode", roleCode);

        return new LoginResult(token, userInfo);
    }

    /**
     * 登出 — 删除 Redis 中的 token
     */
    public void logout(String token) {
        Long userId = JwtUtil.getUserId(token);
        if (userId != null) {
            String redisKey = REDIS_KEY_PREFIX + userId;
            // 只删除当前 token（防止误删新登录的 token）
            String storedToken = redisTemplate.opsForValue().get(redisKey);
            if (token.equals(storedToken)) {
                redisTemplate.delete(redisKey);
            }
        }
    }

    /**
     * 校验 Token 是否有效
     *
     * @param token JWT token
     * @return true=有效，false=无效或已被替换
     */
    public boolean isTokenValid(String token) {
        if (token == null || token.isEmpty()) return false;

        // 1. JWT 格式校验
        if (!JwtUtil.isValid(token)) return false;

        // 2. Redis 中是否存在且匹配
        Long userId = JwtUtil.getUserId(token);
        if (userId == null) return false;

        String redisKey = REDIS_KEY_PREFIX + userId;
        String storedToken = redisTemplate.opsForValue().get(redisKey);

        return token.equals(storedToken);
    }

    /**
     * 根据 Token 获取当前用户信息
     */
    public Optional<SysUser> getCurrentUser(String token) {
        Long userId = JwtUtil.getUserId(token);
        if (userId == null) return Optional.empty();
        return userRepository.findById(userId);
    }

    /**
     * 根据 Token 构建当前登录用户上下文（含角色信息），供拦截器写入请求属性。
     */
    public Optional<LoginUser> getLoginUser(String token) {
        return getCurrentUser(token).map(user -> {
            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(user.getId());
            loginUser.setUsername(user.getUsername());
            loginUser.setRealName(user.getRealName());
            loginUser.setRoleId(user.getRoleId());
            if (user.getRoleId() != null) {
                roleRepository.findById(user.getRoleId()).ifPresent(role -> {
                    loginUser.setRoleName(role.getRoleName());
                    loginUser.setRoleCode(role.getRoleCode());
                });
            }
            return loginUser;
        });
    }

    /**
     * 获取角色可访问的菜单ID集合（用于 RBAC 行级过滤）。
     * <p>菜单列表、文档列表、全局搜索等查询接口据此过滤「当前角色可见」的数据。</p>
     *
     * @param roleId 角色ID
     * @return 该角色关联的菜单ID集合；roleId 为 null 时返回空集合
     */
    public Set<Long> findModuleIdsByRole(Long roleId) {
        if (roleId == null) {
            return Collections.emptySet();
        }
        return roleMenuRepository.findByRoleId(roleId).stream()
                .map(SysRoleRelation::getModuleId)
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户可访问的菜单列表
     */
    public List<SysModule> getMenusByUserRole(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }

        List<SysRoleRelation> roleMenus = roleMenuRepository.findByRoleId(roleId);
        if (roleMenus.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> moduleIds = roleMenus.stream()
                .map(SysRoleRelation::getModuleId)
                .collect(Collectors.toList());

        return menuRepository.findAllById(moduleIds);
    }

    // ==================== 内部类 ====================

    public static class LoginResult {
        private final String token;
        private final Map<String, Object> userInfo;

        public LoginResult(String token, Map<String, Object> userInfo) {
            this.token = token;
            this.userInfo = userInfo;
        }

        public String getToken() { return token; }
        public Map<String, Object> getUserInfo() { return userInfo; }
    }
}
