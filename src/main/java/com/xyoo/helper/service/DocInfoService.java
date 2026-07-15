package com.xyoo.helper.service;

import com.xyoo.helper.entity.DocHistory;
import com.xyoo.helper.entity.DocInfo;
import com.xyoo.helper.entity.SysMenu;
import com.xyoo.helper.repository.DocHistoryRepository;
import com.xyoo.helper.repository.DocInfoRepository;
import com.xyoo.helper.repository.SysMenuRepository;
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
    private final SysMenuRepository sysMenuRepository;

    public DocInfoService(DocInfoRepository docInfoRepository,
                          DocHistoryRepository docHistoryRepository,
                          SysMenuRepository sysMenuRepository) {
        this.docInfoRepository = docInfoRepository;
        this.docHistoryRepository = docHistoryRepository;
        this.sysMenuRepository = sysMenuRepository;
    }

    // ==================== 查询 ====================

    /**
     * 根据所属菜单查询展示的文档列表；menuId 为 null 时返回全部可见文档
     */
    @Transactional(readOnly = true)
    public List<DocInfo> listByMenuId(Long menuId) {
        List<DocInfo> docs;
        if (menuId == null) {
            docs = docInfoRepository.findByIsVisibleOrderByCreateTimeDesc(true);
        } else {
            docs = docInfoRepository.findByMenuIdAndIsVisibleOrderByCreateTimeDesc(menuId, true);
        }
        fillMenuNames(docs);
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
     * 模糊搜索：匹配文档标题、标签、内容
     */
    @Transactional(readOnly = true)
    public List<DocInfo> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<DocInfo> docs = docInfoRepository.searchByKeyword(keyword.trim(), true);
        fillMenuNames(docs);
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
        if (doc.getMenuId() != null) {
            existing.setMenuId(doc.getMenuId());
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
    }

    // ==================== 私有方法 ====================

    /**
     * 批次填充文档的所属菜单名称
     */
    private void fillMenuNames(List<DocInfo> docs) {
        if (docs == null || docs.isEmpty()) return;
        Set<Long> menuIds = docs.stream()
                .map(DocInfo::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (menuIds.isEmpty()) return;
        Map<Long, String> menuNameMap = sysMenuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(SysMenu::getId, SysMenu::getMenuName, (a, b) -> a));
        for (DocInfo doc : docs) {
            if (doc.getMenuId() != null) {
                doc.setMenuName(menuNameMap.getOrDefault(doc.getMenuId(), "—"));
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
