package com.xyoo.helper.config;

import com.xyoo.helper.entity.SysModule;
import com.xyoo.helper.entity.SysRole;
import com.xyoo.helper.entity.SysRoleRelation;
import com.xyoo.helper.rag.QdrantService;
import com.xyoo.helper.repository.SysModuleRepository;
import com.xyoo.helper.repository.SysRoleRelationRepository;
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
import java.util.List;
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
    private final SysRoleRelationRepository roleMenuRepository;
    private final SysModuleRepository menuRepository;
    private final QdrantService qdrantService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PersistenceContext
    private EntityManager em;

    public DataInitializer(SysParamService sysParamService,
                            SysUserRepository userRepository,
                            SysRoleRepository roleRepository,
                            SysRoleRelationRepository roleMenuRepository,
                            SysModuleRepository menuRepository,
                            QdrantService qdrantService) {
        this.sysParamService = sysParamService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roleMenuRepository = roleMenuRepository;
        this.menuRepository = menuRepository;
        this.qdrantService = qdrantService;
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

        // === 文档转换菜单（PDF -> Word），挂在「全站检索」同级，供 RBAC 与菜单管理使用 ===
        ensureDocConvertMenu();

        // === 确保 RAG 向量集合存在（Qdrant 不可用时仅告警，不影响启动）===
        try {
            qdrantService.ensureCollection();
        } catch (Exception e) {
            log.warn("启动时确保 Qdrant 集合失败（RAG 检索将不可用，可稍后重启或手动创建）: {}", e.getMessage());
        }

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
        // 确保 ADMIN 角色关联当前全部 sys_module（含运行时通过模块管理新增的模块）
        Optional<SysRole> adminRole = roleRepository.findById(1L);
        if (adminRole.isEmpty()) return;

        // 先清空，再按当前模块表全量回插，保证与 sys_module 始终一致
        roleMenuRepository.deleteByRoleId(1L);
        List<SysModule> allModules = menuRepository.findAll();
        for (SysModule m : allModules) {
            em.createNativeQuery(
                            "INSERT IGNORE INTO sys_role_relation (role_id, module_id) VALUES (:rid, :mid)")
                    .setParameter("rid", 1L)
                    .setParameter("mid", m.getId())
                    .executeUpdate();
        }
        log.info("管理员角色已关联全部 {} 个模块", allModules.size());
    }

    /**
     * 确保存在「文档转换」菜单（PDF → Word）。
     * 顶层菜单（parentId=0），用于 RBAC 与菜单管理；前端在「全站检索」旁新增入口。
     */
    private void ensureDocConvertMenu() {
        Long id = 100012L;
        if (menuRepository.existsById(id)) {
            return;
        }
        em.createNativeQuery(
                        "INSERT IGNORE INTO sys_module (id, parent_id, module_name, module_path, module_icon, sort_order, is_visible) "
                                + "VALUES (:id, 0, '文档转换', '/doc-convert', '', 12, 1)")
                .setParameter("id", id)
                .executeUpdate();
        log.info("已创建菜单：文档转换（/doc-convert）");
    }
}
