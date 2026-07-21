package com.xyoo.helper.service;

import com.xyoo.helper.entity.DocHistory;
import com.xyoo.helper.entity.DocInfo;
import com.xyoo.helper.entity.SysModule;
import com.xyoo.helper.rag.IndexService;
import com.xyoo.helper.repository.DocHistoryRepository;
import com.xyoo.helper.repository.DocInfoRepository;
import com.xyoo.helper.repository.SysModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档管理业务逻辑层
 * <p>
 * 所有查询均只返回 isVisible=true 的文档（通过读取接口显式过滤）。
 * 编辑操作自动记录到 doc_history 拉链表。
 * </p>
 */
@Service
public class DocInfoService {

    /** 文档内容最大长度 */
    private static final int MAX_CONTENT_LENGTH = 5000;

    private final DocInfoRepository docInfoRepository;
    private final DocHistoryRepository docHistoryRepository;
    private final SysModuleRepository sysMenuRepository;
    private final IndexService indexService;

    public DocInfoService(DocInfoRepository docInfoRepository,
                          DocHistoryRepository docHistoryRepository,
                          SysModuleRepository sysMenuRepository,
                          IndexService indexService) {
        this.docInfoRepository = docInfoRepository;
        this.docHistoryRepository = docHistoryRepository;
        this.sysMenuRepository = sysMenuRepository;
        this.indexService = indexService;
    }

    // ==================== 查询 ====================

    /**
     * 根据所属菜单查询展示的文档列表；moduleId 为 null 时返回全部可见文档
     */
    @Transactional(readOnly = true)
    public List<DocInfo> listByModuleId(Long moduleId) {
        List<DocInfo> docs;
        if (moduleId == null) {
            docs = docInfoRepository.findByIsVisibleOrderByCreateTimeDesc(true);
        } else {
            docs = docInfoRepository.findByModuleIdAndIsVisibleOrderByCreateTimeDesc(moduleId, true);
        }
        fillModuleNames(docs);
        return docs;
    }

    /**
     * 根据所属菜单查询当前角色可见的展示文档列表（RBAC 过滤）
     * <p>
     * allowedModuleIds 为当前登录人角色可访问的菜单ID集合；为空表示无权限，返回空列表。
     * 若显式传入 moduleId，则仅当该 moduleId 在允许集合内时才查询，否则返回空。
     * </p>
     *
     * @param moduleId         所属菜单ID（可为 null，表示查允许集合内的全部菜单）
     * @param allowedModuleIds 角色可见菜单ID集合
     */
    @Transactional(readOnly = true)
    public List<DocInfo> listByModuleId(Long moduleId, Set<Long> allowedModuleIds) {
        if (allowedModuleIds == null || allowedModuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<DocInfo> docs;
        if (moduleId == null) {
            docs = docInfoRepository.findByModuleIdInAndIsVisibleOrderByCreateTimeDesc(allowedModuleIds, true);
        } else {
            if (!allowedModuleIds.contains(moduleId)) {
                return Collections.emptyList();
            }
            docs = docInfoRepository.findByModuleIdAndIsVisibleOrderByCreateTimeDesc(moduleId, true);
        }
        fillModuleNames(docs);
        return docs;
    }

    /**
     * 根据文档业务ID查询文档详情
     */
    @Transactional(readOnly = true)
    public Optional<DocInfo> getByDocId(String docId) {
        return docInfoRepository.findByDocId(docId);
    }

    /**
     * 根据标签关键词查询展示的文档列表
     */
    @Transactional(readOnly = true)
    public List<DocInfo> listByTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return docInfoRepository.findByDocTagsContainingAndIsVisibleOrderByCreateTimeDesc(tag.trim(), true);
    }

    /**
     * 根据标签查询当前角色可见的展示文档列表（RBAC 过滤）
     *
     * @param tag            标签关键词
     * @param allowedModuleIds 角色可见菜单ID集合；为空表示无权限，返回空列表
     */
    @Transactional(readOnly = true)
    public List<DocInfo> listByTag(String tag, Set<Long> allowedModuleIds) {
        if (allowedModuleIds == null || allowedModuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (tag == null || tag.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return docInfoRepository.findByDocTagsContainingAndModuleIdInAndIsVisibleOrderByCreateTimeDesc(
                tag.trim(), allowedModuleIds, true);
    }

    /**
     * 模糊搜索：匹配文档标题、标签、内容
     */
    @Transactional(readOnly = true)
    public List<DocInfo> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<DocInfo> docs = docInfoRepository.searchByKeyword(keyword.trim(), true);
        fillModuleNames(docs);
        return docs;
    }

    /**
     * 全局搜索：模糊匹配标题/标签/内容，仅返回当前角色可见菜单下的展示文档（RBAC 过滤）
     *
     * @param keyword        搜索关键词
     * @param allowedModuleIds 角色可见菜单ID集合；为空表示无权限，返回空列表
     */
    @Transactional(readOnly = true)
    public List<DocInfo> search(String keyword, Set<Long> allowedModuleIds) {
        if (allowedModuleIds == null || allowedModuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<DocInfo> docs = docInfoRepository.searchByKeywordAndModuleIds(keyword.trim(), allowedModuleIds, true);
        fillModuleNames(docs);
        return docs;
    }

    /**
     * 根据主键ID查询（含隐藏的，内部使用）
     */
    @Transactional(readOnly = true)
    public Optional<DocInfo> getById(Long id) {
        return docInfoRepository.findById(id);
    }

    /**
     * 查询文档的所有编辑历史（按版本倒序）
     */
    @Transactional(readOnly = true)
    public List<DocHistory> getHistory(Long docInfoId) {
        return docHistoryRepository.findByDocInfoIdOrderByVersionDesc(docInfoId);
    }

    // ==================== 新增 ====================

    /**
     * 新增文档，自动生成 docId 并记录创建历史
     */
    @Transactional
    public DocInfo create(DocInfo doc) {
        // 生成业务文档ID
        doc.setDocId(generateDocId());

        // 内容截断
        if (doc.getDocContent() != null && doc.getDocContent().length() > MAX_CONTENT_LENGTH) {
            doc.setDocContent(doc.getDocContent().substring(0, MAX_CONTENT_LENGTH));
        }

        if (doc.getIsVisible() == null) {
            doc.setIsVisible(true);
        }

        DocInfo saved = docInfoRepository.save(doc);

        // 记录创建历史（拉链表）
        saveHistory(saved, "CREATE", "新建文档", null);

        // 触发向量索引（失败不影响保存，详见 IndexService 内部容错）
        indexService.indexDocument(saved);

        return saved;
    }

    // ==================== 编辑 ====================

    /**
     * 编辑文档，自动记录编辑历史到拉链表
     *
     * @param docId    文档业务ID
     * @param doc      更新的文档信息
     * @param operator 操作人
     * @param summary  变更摘要
     */
    @Transactional
    public DocInfo update(String docId, DocInfo doc, String operator, String summary) {
        DocInfo existing = docInfoRepository.findByDocId(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在，docId=" + docId));

        if (doc.getDocTitle() != null && !doc.getDocTitle().trim().isEmpty()) {
            existing.setDocTitle(doc.getDocTitle().trim());
        }
        if (doc.getDocTags() != null) {
            existing.setDocTags(doc.getDocTags());
        }
        if (doc.getDocContent() != null) {
            String content = doc.getDocContent();
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH);
            }
            existing.setDocContent(content);
        }
        if (doc.getModuleId() != null) {
            existing.setModuleId(doc.getModuleId());
        }
        if (doc.getOriginalPath() != null) {
            existing.setOriginalPath(doc.getOriginalPath());
        }
        if (doc.getAttachPwd() != null && !doc.getAttachPwd().trim().isEmpty()) {
            existing.setAttachPwd(doc.getAttachPwd());
        }
        if (doc.getIsVisible() != null) {
            existing.setIsVisible(doc.getIsVisible());
        }

        DocInfo saved = docInfoRepository.save(existing);

        // 记录编辑历史（拉链表）
        saveHistory(saved, "UPDATE", summary, operator);

        // 触发向量索引重建（内容/标题/标签/所属菜单变化都需重建）
        indexService.indexDocument(saved);

        return saved;
    }

    // ==================== 标签全量更新 ====================

    /**
     * 全量更新文档标签（用 | 分隔），自动去重去空
     *
     * @param docId   文档业务ID
     * @param docTags 标签字符串，格式如 "用户手册|技术文档|指南"
     */
    @Transactional
    public DocInfo updateTags(String docId, String docTags) {
        DocInfo existing = docInfoRepository.findByDocId(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在，docId=" + docId));

        // 校验并清洗标签：按｜分割、去空白、去空、去重
        if (docTags != null && !docTags.trim().isEmpty()) {
            String cleaned = java.util.Arrays.stream(docTags.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .reduce((a, b) -> a + "|" + b)
                    .orElse("");
            existing.setDocTags(cleaned);
        } else {
            existing.setDocTags(""); // 清空标签
        }

        DocInfo saved = docInfoRepository.save(existing);

        // 记录编辑历史
        saveHistory(saved, "UPDATE", "更新标签", "");

        return saved;
    }

    // ==================== 删除 ====================

    /**
     * 删除文档（同时删除所有编辑历史）
     */
    @Transactional
    public void delete(String docId) {
        DocInfo existing = docInfoRepository.findByDocId(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在，docId=" + docId));

        // 删除编辑历史
        List<DocHistory> histories = docHistoryRepository.findByDocInfoIdOrderByVersionDesc(existing.getId());
        docHistoryRepository.deleteAll(histories);

        // 删除文档
        docInfoRepository.delete(existing);

        // 同步删除向量索引
        indexService.removeDocument(existing.getDocId());
    }

    // ==================== 私有方法 ====================

    /**
     * 批次填充文档的所属菜单名称
     */
    private void fillModuleNames(List<DocInfo> docs) {
        if (docs == null || docs.isEmpty()) return;
        Set<Long> moduleIds = docs.stream()
                .map(DocInfo::getModuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (moduleIds.isEmpty()) return;
        Map<Long, String> moduleNameMap = sysMenuRepository.findAllById(moduleIds).stream()
                .collect(Collectors.toMap(SysModule::getId, SysModule::getModuleName, (a, b) -> a));
        for (DocInfo doc : docs) {
            if (doc.getModuleId() != null) {
                doc.setModuleName(moduleNameMap.getOrDefault(doc.getModuleId(), "—"));
            }
        }
    }

    /**
     * 生成文档业务ID
     */
    private String generateDocId() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "DOC-" + uuid;
    }

    /**
     * 保存编辑历史到拉链表
     */
    private void saveHistory(DocInfo doc, String operationType, String summary, String operator) {
        // 计算下一个版本号
        DocHistory latest = docHistoryRepository.findTopByDocInfoIdOrderByVersionDesc(doc.getId());
        int nextVersion = (latest != null) ? latest.getVersion() + 1 : 1;

        DocHistory history = new DocHistory();
        history.setDocInfoId(doc.getId());
        history.setDocTitle(doc.getDocTitle());
        history.setDocTags(doc.getDocTags());
        history.setDocContent(doc.getDocContent());
        history.setChangeSummary(summary != null ? summary : "");
        history.setOperationType(operationType);
        history.setOperator(operator != null ? operator : "");
        history.setVersion(nextVersion);

        docHistoryRepository.save(history);
    }
}
