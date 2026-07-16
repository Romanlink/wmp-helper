package com.xyoo.helper.config;

import com.xyoo.helper.entity.SysRole;
import com.xyoo.helper.entity.SysRoleMenu;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager em;

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
            // 注意：实体使用 @GeneratedValue(IDENTITY)，Hibernate 6 下 save() 会把手动设主键的
            // 实体按「游离态」走 merge 而报错；此处用原生 INSERT 显式指定主键，等价于原逻辑。
            em.createNativeQuery(
                            "INSERT INTO sys_role (id, role_name, role_code, description, create_time) " +
                            "VALUES (:id, :name, :code, :desc, CURRENT_TIMESTAMP)")
                    .setParameter("id", id)
                    .setParameter("name", name)
                    .setParameter("code", code)
                    .setParameter("desc", desc)
                    .executeUpdate();
            log.info("创建角色: {} ({})", name, code);
        }
    }

    private void initUserIfAbsent(String username, String rawPassword, String realName, Long roleId) {
        if (!userRepository.existsByUsername(username)) {
            em.createNativeQuery(
                            "INSERT INTO sys_user (username, password, real_name, role_id, status, create_time, update_time) " +
                            "VALUES (:username, :password, :realName, :roleId, :status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                    .setParameter("username", username)
                    .setParameter("password", passwordEncoder.encode(rawPassword))
                    .setParameter("realName", realName)
                    .setParameter("roleId", roleId)
                    .setParameter("status", true)
                    .executeUpdate();
            log.info("创建用户: {} ({})", username, realName);
        }
    }

    private void ensureAdminAllMenus() {
        // 确保 ADMIN 角色关联所有 sys_menu
        Optional<SysRole> adminRole = roleRepository.findById(1L);
        if (adminRole.isEmpty()) return;

        // 删除旧的关联（菜单关联由 schema.sql 维护，详见原注释）
        roleMenuRepository.deleteByRoleId(1L);
        log.info("管理员角色菜单关联由 schema.sql 维护");
    }
}
