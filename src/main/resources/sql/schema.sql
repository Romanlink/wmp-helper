-- ============================================================
-- Helper 后台管理系统 - 数据库初始化 SQL
-- ============================================================
-- 说明：
--   本文件使用 CREATE TABLE IF NOT EXISTS 和 INSERT IGNORE INTO，
--   确保重复执行安全（幂等），不会覆盖已有数据。
--   生产环境通过 Spring Boot spring.sql.init.mode=always 自动执行。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 系统菜单表 sys_module
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_module` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '菜单ID，主键',
    `parent_id`     BIGINT          NOT NULL DEFAULT 0         COMMENT '父菜单ID，0表示顶级菜单',
    `module_name`     VARCHAR(32)     NOT NULL                   COMMENT '菜单名称',
    `module_path`     VARCHAR(128)    DEFAULT ''                 COMMENT '菜单路由路径/URL',
    `module_icon`     VARCHAR(64)     DEFAULT ''                 COMMENT '菜单图标（CSS类名或图标名称）',
    `sort_order`    INT             NOT NULL DEFAULT 0         COMMENT '排序序号，数值越小越靠前',
    `is_visible`    TINYINT(1)      NOT NULL DEFAULT 1         COMMENT '是否展示：0=隐藏，1=展示',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_is_visible` (`is_visible`),
    INDEX `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统菜单表';

-- 初始化菜单数据（幂等，主键重复则跳过）
INSERT IGNORE INTO `sys_module` (`id`, `parent_id`, `module_name`, `module_path`, `module_icon`, `sort_order`, `is_visible`) VALUES
(100001, 0, '组合宝',    '/', '', 1,  1),
(100002, 0, '理财',      '/', '', 2,  1),
(100003, 0, '基金',      '/', '', 3,  1),
(100004, 0, '定投',      '/', '', 4,  1),
(100005, 0, '保险',      '/', '', 5,  1),
(100006, 0, '信托',      '/', '', 6,  1),
(100007, 0, '贵金属',    '/', '', 7,  1),
(100008, 0, '产品中心',  '/', '', 8,  1),
(100009, 0, '客户中心',  '/', '', 9,  1),
(100010, 0, '积存金',    '/', '', 10, 1),
(100011, 0, '其他',      '/', '', 11, 1),
(100012, 0, '文档转换',  '/doc-convert', '', 12, 1);

-- ------------------------------------------------------------
-- 2. 文档信息表 doc_info
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `doc_info` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    `doc_id`        VARCHAR(32)     NOT NULL                   COMMENT '文档业务ID（UUID）',
    `original_path` VARCHAR(512)    DEFAULT ''                 COMMENT '原始文件路径（PDF等本地文件）',
    `attach_pwd`    VARCHAR(256)    DEFAULT ''                 COMMENT '附件加密密码（AES-GCM）',
    `module_id`       BIGINT          NOT NULL                   COMMENT '所属菜单ID，关联 sys_module.id',
    `doc_title`     VARCHAR(256)    NOT NULL                   COMMENT '文档标题',
    `doc_tags`      VARCHAR(512)    DEFAULT ''                 COMMENT '文档标签，多个用竖线分隔',
    `doc_content`   TEXT                                       COMMENT '文档内容（Markdown 语法）',
    `is_visible`    TINYINT(1)      NOT NULL DEFAULT 1         COMMENT '是否展示：0=隐藏，1=展示',
    `indexed`       TINYINT(1)      NOT NULL DEFAULT 0         COMMENT '是否已建立向量索引：0=未索引，1=已索引',
    `embedding_version` VARCHAR(64) DEFAULT ''                 COMMENT '索引所用 embedding 模型版本，用于模型升级后全量重建',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_doc_id` (`doc_id`),
    INDEX `idx_module_id` (`module_id`),
    INDEX `idx_is_visible` (`is_visible`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档信息表';

-- ------------------------------------------------------------
-- 3. 文档编辑历史表 doc_history（拉链表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `doc_history` (
    `id`             BIGINT          NOT NULL AUTO_INCREMENT   COMMENT '主键ID',
    `doc_info_id`    BIGINT          NOT NULL                  COMMENT '关联文档ID，关联 doc_info.id',
    `doc_title`      VARCHAR(256)    NOT NULL                  COMMENT '编辑时的文档标题快照',
    `doc_tags`       VARCHAR(512)    DEFAULT ''                COMMENT '编辑时的标签快照',
    `doc_content`    TEXT                                      COMMENT '编辑时的内容快照',
    `change_summary` VARCHAR(512)    DEFAULT ''                COMMENT '本次变更摘要说明',
    `operation_type` VARCHAR(32)     NOT NULL                  COMMENT '操作类型：CREATE 新建 / UPDATE 编辑',
    `operator`       VARCHAR(64)     DEFAULT ''                COMMENT '操作人',
    `version`        INT             NOT NULL DEFAULT 1        COMMENT '版本号，从1开始递增',
    `operate_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    INDEX `idx_doc_info_id` (`doc_info_id`),
    INDEX `idx_operate_time` (`operate_time`),
    INDEX `idx_operation_type` (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档编辑历史表（拉链表）';

-- ------------------------------------------------------------
-- 4. 系统参数表 sys_param
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_param` (
    `param_id`    VARCHAR(64)  NOT NULL COMMENT '参数标识（唯一键）',
    `param_value` VARCHAR(256) NOT NULL COMMENT '参数值',
    `param_desc`  VARCHAR(512) DEFAULT '' COMMENT '参数说明',
    PRIMARY KEY (`param_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统参数表';

-- 初始化系统参数（幂等，主键重复则跳过）
INSERT IGNORE INTO `sys_param` (`param_id`, `param_value`, `param_desc`) VALUES
('doc_editable',     '1', '文档是否可编辑（1=可编辑, 0=只读）'),
('doc_downloadable', '1', '文档是否可下载（1=可下载, 0=禁止下载）');

-- ------------------------------------------------------------
-- 5. 系统角色表 sys_role
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT    COMMENT '角色ID，主键',
    `role_name`   VARCHAR(64)  NOT NULL                   COMMENT '角色名称',
    `role_code`   VARCHAR(64)  NOT NULL                   COMMENT '角色编码（唯一）',
    `description` VARCHAR(256) DEFAULT ''                 COMMENT '角色描述',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统角色表';

-- 初始化角色数据
INSERT IGNORE INTO `sys_role` (`id`, `role_name`, `role_code`, `description`) VALUES
(1, '管理员', 'ADMIN',   '系统管理员，拥有全部菜单权限'),
(2, '编辑员', 'EDITOR',  '文档编辑员，可管理文档'),
(3, '访客',   'GUEST',    '只读访客，仅可浏览文档');

-- ------------------------------------------------------------
-- 6. 系统用户表 sys_user
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT    COMMENT '用户ID，主键',
    `username`    VARCHAR(64)  NOT NULL                   COMMENT '登录用户名',
    `password`    VARCHAR(128) NOT NULL                   COMMENT 'BCrypt加密密码',
    `real_name`   VARCHAR(64)  DEFAULT ''                 COMMENT '真实姓名',
    `role_id`     BIGINT       DEFAULT NULL                COMMENT '角色ID，关联 sys_role.id',
    `status`      TINYINT(1)   NOT NULL DEFAULT 1         COMMENT '状态：1=启用，0=禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户表';

-- 初始化用户数据由 DataInitializer 在 Java 代码中创建（使用 BCrypt 编码密码）

-- ------------------------------------------------------------
-- 7. 角色-菜单关联表 sys_role_relation
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role_relation` (
    `role_id`  BIGINT NOT NULL COMMENT '角色ID，关联 sys_role.id',
    `module_id`  BIGINT NOT NULL COMMENT '菜单ID，关联 sys_module.id',
    PRIMARY KEY (`role_id`, `module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色-菜单关联表';

-- 管理员角色关联所有菜单
INSERT IGNORE INTO `sys_role_relation` (`role_id`, `module_id`)
SELECT 1, `id` FROM `sys_module`;

-- 编辑员、访客角色同样关联所有菜单（角色菜单权限的细化可在 sys_role_relation 中调整）
INSERT IGNORE INTO `sys_role_relation` (`role_id`, `module_id`)
SELECT 2, `id` FROM `sys_module`;

INSERT IGNORE INTO `sys_role_relation` (`role_id`, `module_id`)
SELECT 3, `id` FROM `sys_module`;

-- ------------------------------------------------------------
-- 8. 文档切片向量表 doc_chunk（RAG 检索增强）
-- ------------------------------------------------------------
-- 说明：
--   仅持久化切片原文与元数据，向量本身存储在 Qdrant 中（point_id = chunk 的 UUID）。
--   本表用于「按文档删除/重建索引」时定位 Qdrant 中的点，以及人工排查。
--   向量维度与 helper.embedding.dim / helper.qdrant.dim 保持一致（bge-m3 = 1024）。
CREATE TABLE IF NOT EXISTS `doc_chunk` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT    COMMENT '主键ID',
    `doc_id`            VARCHAR(32)     NOT NULL                   COMMENT '所属文档业务ID，关联 doc_info.doc_id',
    `module_id`         BIGINT          NOT NULL                   COMMENT '所属菜单ID，关联 sys_module.id（RAG 检索 RBAC 过滤用）',
    `chunk_index`       INT             NOT NULL DEFAULT 0         COMMENT '切片序号，从0开始',
    `content`           TEXT                                        COMMENT '切片原文（Markdown 片段）',
    `embedding_version` VARCHAR(64)     DEFAULT ''                 COMMENT '索引所用 embedding 模型版本',
    `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_doc_id` (`doc_id`),
    INDEX `idx_module_id` (`module_id`),
    INDEX `idx_chunk_index` (`doc_id`, `chunk_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档切片向量表（RAG）';
