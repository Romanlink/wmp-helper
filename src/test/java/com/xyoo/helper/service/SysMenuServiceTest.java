package com.xyoo.helper.service;

import com.xyoo.helper.entity.SysMenu;
import com.xyoo.helper.repository.SysMenuRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
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
 * SysMenuService 单元测试（纯 Mockito）。
 * 覆盖列表/搜索/子菜单查询、新增默认值、编辑（含父菜单自引用校验）、级联删除。
 */
@ExtendWith(MockitoExtension.class)
class SysMenuServiceTest {

    @Mock private SysMenuRepository menuRepository;

    @InjectMocks private SysMenuService sysMenuService;

    @Captor private ArgumentCaptor<SysMenu> menuCaptor;

    @Test
    @DisplayName("listVisibleMenus 委托仓储")
    void listVisibleMenus() {
        given(menuRepository.findAllByIsVisibleOrderBySortOrderAsc(true)).willReturn(Collections.emptyList());
        sysMenuService.listVisibleMenus();
        verify(menuRepository).findAllByIsVisibleOrderBySortOrderAsc(true);
    }

    @Test
    @DisplayName("searchMenus 空关键词等价于列出全部可见菜单")
    void searchMenus_empty() {
        given(menuRepository.findAllByIsVisibleOrderBySortOrderAsc(true)).willReturn(Collections.emptyList());
        sysMenuService.searchMenus("  ");
        verify(menuRepository).findAllByIsVisibleOrderBySortOrderAsc(true);
        verify(menuRepository, never())
                .findByMenuNameContainingAndIsVisibleOrderBySortOrderAsc(anyString(), any());
    }

    @Test
    @DisplayName("searchMenus 正常关键词搜索")
    void searchMenus_normal() {
        given(menuRepository.findByMenuNameContainingAndIsVisibleOrderBySortOrderAsc("用户", true))
                .willReturn(Collections.emptyList());
        sysMenuService.searchMenus("用户");
        verify(menuRepository).findByMenuNameContainingAndIsVisibleOrderBySortOrderAsc("用户", true);
    }

    @Test
    @DisplayName("getChildMenus(null) 视为顶级菜单 parentId=0")
    void getChildMenus_nullParent() {
        given(menuRepository.findByParentIdAndIsVisibleOrderBySortOrderAsc(0L, true))
                .willReturn(Collections.emptyList());
        sysMenuService.getChildMenus(null);
        verify(menuRepository).findByParentIdAndIsVisibleOrderBySortOrderAsc(0L, true);
    }

    @Test
    @DisplayName("getChildMenus(5) 按父ID查询")
    void getChildMenus_byParent() {
        given(menuRepository.findByParentIdAndIsVisibleOrderBySortOrderAsc(5L, true))
                .willReturn(Collections.emptyList());
        sysMenuService.getChildMenus(5L);
        verify(menuRepository).findByParentIdAndIsVisibleOrderBySortOrderAsc(5L, true);
    }

    @Test
    @DisplayName("getById 委托仓储")
    void getById() {
        given(menuRepository.findById(1L)).willReturn(Optional.of(new SysMenu()));
        assertThat(sysMenuService.getById(1L)).isPresent();
    }

    @Test
    @DisplayName("create 补全默认值：parentId=0、sortOrder=0、isVisible=true")
    void create_defaults() {
        SysMenu m = new SysMenu();
        m.setMenuName("用户手册");
        given(menuRepository.save(any(SysMenu.class))).willAnswer(i -> i.getArgument(0));

        SysMenu saved = sysMenuService.create(m);

        assertThat(saved.getParentId()).isEqualTo(0L);
        assertThat(saved.getSortOrder()).isEqualTo(0);
        assertThat(saved.getIsVisible()).isTrue();
    }

    @Test
    @DisplayName("update 父菜单设为自身抛 IllegalArgumentException")
    void update_selfParent_throws() {
        SysMenu existing = new SysMenu();
        existing.setId(3L);
        existing.setMenuName("旧");
        given(menuRepository.findById(3L)).willReturn(Optional.of(existing));

        SysMenu patch = new SysMenu();
        patch.setParentId(3L);

        assertThatThrownBy(() -> sysMenuService.update(3L, patch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("父菜单不能是自身");
    }

    @Test
    @DisplayName("update 正常合并字段并保持未提供字段原值")
    void update_normal() {
        SysMenu existing = new SysMenu();
        existing.setId(3L);
        existing.setMenuName("旧");
        existing.setParentId(0L);
        given(menuRepository.findById(3L)).willReturn(Optional.of(existing));
        given(menuRepository.save(any(SysMenu.class))).willAnswer(i -> i.getArgument(0));

        SysMenu patch = new SysMenu();
        patch.setMenuName(" 新名称 ");
        patch.setMenuPath("/x");
        patch.setSortOrder(2);

        SysMenu saved = sysMenuService.update(3L, patch);

        assertThat(saved.getMenuName()).isEqualTo("新名称");
        assertThat(saved.getMenuPath()).isEqualTo("/x");
        assertThat(saved.getSortOrder()).isEqualTo(2);
        assertThat(saved.getParentId()).isEqualTo(0L);
    }

    @Test
    @DisplayName("delete 不存在抛 RuntimeException")
    void delete_notFound() {
        given(menuRepository.existsById(9L)).willReturn(false);
        assertThatThrownBy(() -> sysMenuService.delete(9L)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("delete 无子菜单时仅删除自身")
    void delete_noChildren() {
        given(menuRepository.existsById(1L)).willReturn(true);
        given(menuRepository.findByParentIdOrderBySortOrderAsc(1L)).willReturn(Collections.emptyList());

        sysMenuService.delete(1L);

        verify(menuRepository).deleteById(1L);
    }

    @Test
    @DisplayName("delete 递归删除子菜单")
    void delete_withChildren() {
        SysMenu child = new SysMenu();
        child.setId(2L);
        given(menuRepository.existsById(1L)).willReturn(true);
        given(menuRepository.findByParentIdOrderBySortOrderAsc(1L)).willReturn(Collections.singletonList(child));
        given(menuRepository.findByParentIdOrderBySortOrderAsc(2L)).willReturn(Collections.emptyList());

        sysMenuService.delete(1L);

        verify(menuRepository).deleteById(1L);
        verify(menuRepository).deleteById(2L);
    }
}
