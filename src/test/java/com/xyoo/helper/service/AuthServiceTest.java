package com.xyoo.helper.service;

import com.xyoo.helper.entity.SysMenu;
import com.xyoo.helper.entity.SysRole;
import com.xyoo.helper.entity.SysRoleMenu;
import com.xyoo.helper.entity.SysUser;
import com.xyoo.helper.repository.SysMenuRepository;
import com.xyoo.helper.repository.SysRoleMenuRepository;
import com.xyoo.helper.repository.SysRoleRepository;
import com.xyoo.helper.repository.SysUserRepository;
import com.xyoo.helper.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AuthService 单元测试（纯 Mockito）。
 * 注意：BCryptPasswordEncoder 与 JwtUtil 为真实实现（非 mock），
 * 测试中直接使用它们生成哈希/Token，验证登录态与 Redis 单点登录逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private SysUserRepository userRepository;
    @Mock private SysRoleRepository roleRepository;
    @Mock private SysRoleMenuRepository roleMenuRepository;
    @Mock private SysMenuRepository menuRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private AuthService authService;

    private SysUser user(String encodedPassword) {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("alice");
        u.setPassword(encodedPassword);
        u.setRealName("爱丽丝");
        u.setRoleId(2L);
        u.setStatus(true);
        return u;
    }

    // ==================== 登录 ====================

    @Test
    @DisplayName("login 成功：返回 token、写入 Redis、携带角色信息")
    void login_success() {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        SysUser u = user(enc.encode("secret"));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(u));
        SysRole role = new SysRole();
        role.setId(2L);
        role.setRoleName("管理员");
        role.setRoleCode("admin");
        given(roleRepository.findById(2L)).willReturn(Optional.of(role));
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        AuthService.LoginResult result = authService.login("alice", "secret");

        assertThat(result.getToken()).isNotBlank();
        assertThat(JwtUtil.getUserId(result.getToken())).isEqualTo(1L);
        assertThat(result.getUserInfo())
                .containsEntry("username", "alice")
                .containsEntry("roleName", "管理员")
                .containsEntry("roleCode", "admin");
        verify(valueOps).set(eq("auth:user:1"), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("login 用户不存在抛异常")
    void login_notFound() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login("ghost", "x"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("login 账号被禁用抛异常")
    void login_disabled() {
        SysUser u = user(new BCryptPasswordEncoder().encode("secret"));
        u.setStatus(false);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(u));
        assertThatThrownBy(() -> authService.login("alice", "secret"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("禁用");
    }

    @Test
    @DisplayName("login 密码错误抛异常")
    void login_wrongPassword() {
        SysUser u = user(new BCryptPasswordEncoder().encode("secret"));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(u));
        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    // ==================== 登出 ====================

    @Test
    @DisplayName("logout 当前 token 与 Redis 一致才删除")
    void logout_matched() {
        String token = JwtUtil.generateToken(1L, "alice");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("auth:user:1")).willReturn(token);

        authService.logout(token);

        verify(redisTemplate).delete("auth:user:1");
    }

    @Test
    @DisplayName("logout token 与 Redis 不一致则不删除（防误删新登录 token）")
    void logout_unmatched() {
        String token = JwtUtil.generateToken(1L, "alice");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("auth:user:1")).willReturn("other-token");

        authService.logout(token);

        verify(redisTemplate, never()).delete(anyString());
    }

    // ==================== Token 校验 ====================

    @Test
    @DisplayName("isTokenValid null 返回 false")
    void isTokenValid_null() {
        assertThat(authService.isTokenValid(null)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid 有效且 Redis 匹配返回 true")
    void isTokenValid_valid() {
        String token = JwtUtil.generateToken(1L, "alice");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("auth:user:1")).willReturn(token);

        assertThat(authService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid Redis 中 token 不一致返回 false")
    void isTokenValid_redisMismatch() {
        String token = JwtUtil.generateToken(1L, "alice");
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("auth:user:1")).willReturn("another");

        assertThat(authService.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("getCurrentUser 解析 token 并查库")
    void getCurrentUser() {
        String token = JwtUtil.generateToken(1L, "alice");
        given(userRepository.findById(1L)).willReturn(Optional.of(user("x")));

        assertThat(authService.getCurrentUser(token)).isPresent();
    }

    // ==================== 菜单权限 ====================

    @Test
    @DisplayName("getMenusByUserRole 按角色聚合菜单")
    void getMenusByUserRole() {
        given(roleMenuRepository.findByRoleId(2L))
                .willReturn(Collections.singletonList(new SysRoleMenu(2L, 10L)));
        SysMenu menu = new SysMenu();
        menu.setId(10L);
        menu.setMenuName("用户手册");
        given(menuRepository.findAllById(Collections.singletonList(10L)))
                .willReturn(Collections.singletonList(menu));

        List<SysMenu> menus = authService.getMenusByUserRole(2L);

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getMenuName()).isEqualTo("用户手册");
    }

    @Test
    @DisplayName("getMenusByUserRole roleId 为 null 返回空")
    void getMenusByUserRole_null() {
        assertThat(authService.getMenusByUserRole(null)).isEmpty();
    }
}
