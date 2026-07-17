package com.xyoo.helper.service;

import com.xyoo.helper.entity.DocHistory;
import com.xyoo.helper.entity.DocInfo;
import com.xyoo.helper.entity.SysModule;
import com.xyoo.helper.repository.DocHistoryRepository;
import com.xyoo.helper.repository.DocInfoRepository;
import com.xyoo.helper.repository.SysModuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * DocInfoService 单元测试（纯 Mockito，不加载 Spring 上下文）。
 * 覆盖查询、新增、编辑、标签更新、删除以及内容截断与历史拉链表记录。
 */
@ExtendWith(MockitoExtension.class)
class DocInfoServiceTest {

    @Mock private DocInfoRepository docInfoRepository;
    @Mock private DocHistoryRepository docHistoryRepository;
    @Mock private SysModuleRepository sysMenuRepository;

    @InjectMocks private DocInfoService docInfoService;

    @Captor private ArgumentCaptor<DocHistory> historyCaptor;

    private DocInfo sampleDoc() {
        DocInfo d = new DocInfo();
        d.setId(1L);
        d.setDocId("DOC-OLD");
        d.setModuleId(10L);
        d.setDocTitle("原标题");
        d.setDocTags("a|b");
        d.setDocContent("原内容");
        d.setIsVisible(true);
        return d;
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("listByModuleId(null) 查询全部可见文档并填充菜单名")
    void listByModuleId_all() {
        DocInfo d = sampleDoc();
        given(docInfoRepository.findByIsVisibleOrderByCreateTimeDesc(true))
                .willReturn(Collections.singletonList(d));
        SysModule menu = new SysModule();
        menu.setId(10L);
        menu.setModuleName("用户手册");
        given(sysMenuRepository.findAllById(any())).willReturn(Collections.singletonList(menu));

        List<DocInfo> result = docInfoService.listByModuleId(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getModuleName()).isEqualTo("用户手册");
        verify(docInfoRepository).findByIsVisibleOrderByCreateTimeDesc(true);
    }

    @Test
    @DisplayName("listByModuleId(moduleId) 按菜单过滤")
    void listByModuleId_byMenu() {
        given(docInfoRepository.findByModuleIdAndIsVisibleOrderByCreateTimeDesc(5L, true))
                .willReturn(Collections.emptyList());
        assertThat(docInfoService.listByModuleId(5L)).isEmpty();
        verify(docInfoRepository).findByModuleIdAndIsVisibleOrderByCreateTimeDesc(5L, true);
    }

    @Test
    @DisplayName("getByDocId 委托仓储")
    void getByDocId() {
        given(docInfoRepository.findByDocId("DOC-1")).willReturn(Optional.of(sampleDoc()));
        assertThat(docInfoService.getByDocId("DOC-1")).isPresent();
    }

    @Test
    @DisplayName("listByTag 空关键词返回空且不查库")
    void listByTag_empty() {
        assertThat(docInfoService.listByTag("  ")).isEmpty();
        verify(docInfoRepository, never())
                .findByDocTagsContainingAndIsVisibleOrderByCreateTimeDesc(anyString(), any());
    }

    @Test
    @DisplayName("listByTag 正常关键词委托仓储")
    void listByTag_normal() {
        given(docInfoRepository.findByDocTagsContainingAndIsVisibleOrderByCreateTimeDesc("k", true))
                .willReturn(Collections.emptyList());
        docInfoService.listByTag("k");
        verify(docInfoRepository).findByDocTagsContainingAndIsVisibleOrderByCreateTimeDesc("k", true);
    }

    @Test
    @DisplayName("search 空关键词返回空")
    void search_empty() {
        assertThat(docInfoService.search("")).isEmpty();
    }

    @Test
    @DisplayName("search 正常关键词委托仓储并填充菜单名")
    void search_normal() {
        DocInfo d = sampleDoc();
        given(docInfoRepository.searchByKeyword("关键字", true)).willReturn(Collections.singletonList(d));
        SysModule menu = new SysModule();
        menu.setId(10L);
        menu.setModuleName("用户手册");
        given(sysMenuRepository.findAllById(any())).willReturn(Collections.singletonList(menu));

        List<DocInfo> r = docInfoService.search("关键字");
        assertThat(r.get(0).getModuleName()).isEqualTo("用户手册");
    }

    @Test
    @DisplayName("getById 委托仓储")
    void getById() {
        given(docInfoRepository.findById(1L)).willReturn(Optional.of(sampleDoc()));
        assertThat(docInfoService.getById(1L)).isPresent();
    }

    @Test
    @DisplayName("getHistory 委托仓储按版本倒序")
    void getHistory() {
        given(docHistoryRepository.findByDocInfoIdOrderByVersionDesc(1L)).willReturn(Collections.emptyList());
        docInfoService.getHistory(1L);
        verify(docHistoryRepository).findByDocInfoIdOrderByVersionDesc(1L);
    }

    // ==================== 新增 ====================

    @Test
    @DisplayName("create 自动生成 docId、截断超长内容、记录 CREATE 历史(version=1)")
    void create() {
        given(docInfoRepository.save(any(DocInfo.class))).willAnswer(i -> i.getArgument(0));
        given(docHistoryRepository.findTopByDocInfoIdOrderByVersionDesc(null)).willReturn(null);
        given(docHistoryRepository.save(any(DocHistory.class))).willAnswer(i -> i.getArgument(0));

        DocInfo input = new DocInfo();
        input.setModuleId(10L);
        input.setDocTitle("新文档");
        input.setDocContent(repeat("x", 6000));

        DocInfo saved = docInfoService.create(input);

        assertThat(saved.getDocId()).startsWith("DOC-");
        assertThat(saved.getDocContent()).hasSize(5000);
        assertThat(saved.getIsVisible()).isTrue();

        verify(docHistoryRepository).save(historyCaptor.capture());
        DocHistory h = historyCaptor.getValue();
        assertThat(h.getVersion()).isEqualTo(1);
        assertThat(h.getOperationType()).isEqualTo("CREATE");
        assertThat(h.getDocTitle()).isEqualTo("新文档");
    }

    // ==================== 编辑 ====================

    @Test
    @DisplayName("update 合并非空字段、trim 标题、截断超长内容、记录 UPDATE 历史(version=2)")
    void update() {
        DocInfo existing = sampleDoc();
        given(docInfoRepository.findByDocId("DOC-OLD")).willReturn(Optional.of(existing));
        given(docInfoRepository.save(any(DocInfo.class))).willAnswer(i -> i.getArgument(0));
        DocHistory prev = new DocHistory();
        prev.setVersion(1);
        given(docHistoryRepository.findTopByDocInfoIdOrderByVersionDesc(1L)).willReturn(prev);
        given(docHistoryRepository.save(any(DocHistory.class))).willAnswer(i -> i.getArgument(0));

        DocInfo patch = new DocInfo();
        patch.setDocTitle("  新标题  ");
        patch.setDocContent(repeat("y", 6000));

        DocInfo saved = docInfoService.update("DOC-OLD", patch, "admin", "修改内容");

        assertThat(saved.getDocTitle()).isEqualTo("新标题");
        assertThat(saved.getDocContent()).hasSize(5000);

        verify(docHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getVersion()).isEqualTo(2);
        assertThat(historyCaptor.getValue().getOperationType()).isEqualTo("UPDATE");
        assertThat(historyCaptor.getValue().getOperator()).isEqualTo("admin");
    }

    @Test
    @DisplayName("update 文档不存在抛 RuntimeException")
    void update_notFound() {
        given(docInfoRepository.findByDocId("NOPE")).willReturn(Optional.empty());
        assertThatThrownBy(() -> docInfoService.update("NOPE", new DocInfo(), "a", "b"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("updateTags 清洗去重并用 | 连接，记录历史")
    void updateTags_clean() {
        DocInfo existing = sampleDoc();
        given(docInfoRepository.findByDocId("DOC-OLD")).willReturn(Optional.of(existing));
        given(docInfoRepository.save(any(DocInfo.class))).willAnswer(i -> i.getArgument(0));
        DocHistory prev = new DocHistory();
        prev.setVersion(1);
        given(docHistoryRepository.findTopByDocInfoIdOrderByVersionDesc(1L)).willReturn(prev);
        given(docHistoryRepository.save(any(DocHistory.class))).willAnswer(i -> i.getArgument(0));

        DocInfo saved = docInfoService.updateTags("DOC-OLD", " a | b | a | c ");
        assertThat(saved.getDocTags()).isEqualTo("a|b|c");

        verify(docHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getOperationType()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("updateTags 空值清空标签")
    void updateTags_clear() {
        DocInfo existing = sampleDoc();
        given(docInfoRepository.findByDocId("DOC-OLD")).willReturn(Optional.of(existing));
        given(docInfoRepository.save(any(DocInfo.class))).willAnswer(i -> i.getArgument(0));
        DocHistory prev = new DocHistory();
        prev.setVersion(1);
        given(docHistoryRepository.findTopByDocInfoIdOrderByVersionDesc(1L)).willReturn(prev);
        given(docHistoryRepository.save(any(DocHistory.class))).willAnswer(i -> i.getArgument(0));

        DocInfo saved = docInfoService.updateTags("DOC-OLD", null);
        assertThat(saved.getDocTags()).isEqualTo("");
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("delete 删除文档及其全部历史")
    void delete() {
        DocInfo existing = sampleDoc();
        given(docInfoRepository.findByDocId("DOC-OLD")).willReturn(Optional.of(existing));
        given(docHistoryRepository.findByDocInfoIdOrderByVersionDesc(1L)).willReturn(Collections.emptyList());

        docInfoService.delete("DOC-OLD");

        verify(docHistoryRepository).deleteAll(Collections.emptyList());
        verify(docInfoRepository).delete(existing);
    }

    @Test
    @DisplayName("delete 文档不存在抛 RuntimeException")
    void delete_notFound() {
        given(docInfoRepository.findByDocId("NOPE")).willReturn(Optional.empty());
        assertThatThrownBy(() -> docInfoService.delete("NOPE")).isInstanceOf(RuntimeException.class);
    }
}
