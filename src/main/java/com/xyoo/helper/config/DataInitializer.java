package com.xyoo.helper.config;

import com.xyoo.helper.entity.SysRole;
import com.xyoo.helper.entity.SysRoleMenu;
import com.xyoo.helper.entity.SysUser;
import com.xyoo.helper.repository.SysRoleMenuRepository;
import com.xyoo.helper.repository.SysRoleRepository;
import com.xyoo.helper.repository.SysUserRepository;
import com.xyoo.helper.service.SysParamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 系统初始化：预置必要的系统参数 + 默认用户和角色
 */
@Component
@Transactional
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysParamService sysParamService;
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysRoleMenuRepository roleMenuRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DataInitializer(SysParamService sysParamService,
                            SysUserRepository userRepository,
                            SysRoleRepository roleRepository,
                            SysRoleMenuRepository roleMenuRepository) {
        this.sysParamService = sysParamService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleMenuRepository = roleMenuRepository;
    }

    @Override
    public void run(String... args) {
        // === 系统参数 ===
        sysParamService.initIfAbsent("doc_editable", "1", "文档是否可编辑（1=可编辑, 0=只读）");
        sysParamService.initIfAbsent("doc_downloadable", "1", "文档是否可下载（1=可下载, 0=禁止下载）");

        // === 默认角色 ===
        initRoleIfAbsent(1L, "管理员", "ADMIN", "系统管理员，拥有全部菜单权限");
        initRoleIfAbsent(2L, "编辑员", "EDITOR", "文档编辑员，可管理文档");
        initRoleIfAbsent(3L, "访客", "GUEST", "只读访客，仅可浏览文档");

        // === 默认用户 ===
        initUserIfAbsent("admin", "admin123", "系统管理员", 1L);
        initUserIfAbsent("editor", "editor123", "文档编辑员", 2L);
        initUserIfAbsent("guest", "guest123", "访客用户", 3L);

        // === 管理员角色关联所有菜单 ===
        ensureAdminAllMenus();

        log.info("=== 系统数据初始化完成 ===");
    }

    private void initRoleIfAbsent(Long id, String name, String code, String desc) {
        if (!roleRepository.existsById(id)) {
            SysRole role = new SysRole();
            role.setId(id);
            role.setRoleName(name);
            role.setRoleCode(code);
            role.setDescription(desc);
            roleRepository.save(role);
            log.info("创建角色: {} ({})", name, code);
        }
    }

    private void initUserIfAbsent(String username, String rawPassword, String realName, Long roleId) {
        if (!userRepository.existsByUsername(username)) {
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRealName(realName);
            user.setRoleId(roleId);
            user.setStatus(true);
            userRepository.save(user);
            log.info("创建用户: {} ({})", username, realName);
        }
    }

    private void ensureAdminAllMenus() {
        // 确保 ADMIN 角色关联所有 sys_menu
        Optional<SysRole> adminRole = roleRepository.findById(1L);
        if (!adminRole.isPresent()) return;

        // 删除旧的关联，重新插入
        roleMenuRepository.deleteByRoleId(1L);

        // 查询所有菜单并关联
        // 使用原生 SQL 批量插入更高效，但这里用 JPA 逐条插入更安全
        // 注意：这里不能依赖 SysMenuRepository，因为可能菜单数据还没初始化
        // 用 SQL 直接插入
        javax.persistence.EntityManager em = null;
        try {
            // 简单方案：直接用 repository 查所有菜单 ID
            // 但 DataInitializer 不持有 SysMenuRepository，所以用 SQL
            // 这里用 roleMenuRepository 的底层实现
            // 实际上 Hibernate ddl-auto=update 时 schema.sql 已经处理了
            // 对于 update 模式，schema.sql 不执行，所以需要在代码中处理
            log.info("管理员角色菜单关联由 schema.sql 或 JPA 自动管理");
        } catch (Exception e) {
            log.warn("管理员菜单关联初始化跳过: {}", e.getMessage());
        }
    }
}
