package com.xyoo.helper.service;

import com.xyoo.helper.entity.SysParam;
import com.xyoo.helper.repository.SysParamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * SysParamService 单元测试（纯 Mockito）。
 */
@ExtendWith(MockitoExtension.class)
class SysParamServiceTest {

    @Mock private SysParamRepository sysParamRepository;

    @InjectMocks private SysParamService sysParamService;

    @Test
    @DisplayName("getByParamId 委托仓储")
    void getByParamId() {
        given(sysParamRepository.findById("site.title"))
                .willReturn(Optional.of(new SysParam("site.title", "标题", "")));
        assertThat(sysParamService.getByParamId("site.title")).isPresent();
    }

    @Test
    @DisplayName("getValue 存在时返回参数值")
    void getValue_present() {
        given(sysParamRepository.findById("k")).willReturn(Optional.of(new SysParam("k", "v", "")));
        assertThat(sysParamService.getValue("k", "def")).isEqualTo("v");
    }

    @Test
    @DisplayName("getValue 不存在时返回默认值")
    void getValue_absent() {
        given(sysParamRepository.findById("missing")).willReturn(Optional.empty());
        assertThat(sysParamService.getValue("missing", "def")).isEqualTo("def");
    }

    @Test
    @DisplayName("save 委托仓储")
    void save() {
        SysParam p = new SysParam("k", "v", "");
        given(sysParamRepository.save(any(SysParam.class))).willReturn(p);
        assertThat(sysParamService.save(p)).isEqualTo(p);
    }

    @Test
    @DisplayName("initIfAbsent 不存在时插入")
    void initIfAbsent_absent() {
        given(sysParamRepository.existsById("new")).willReturn(false);
        given(sysParamRepository.save(any(SysParam.class))).willReturn(new SysParam("new", "v", ""));

        sysParamService.initIfAbsent("new", "v", "desc");

        verify(sysParamRepository).save(any(SysParam.class));
    }

    @Test
    @DisplayName("initIfAbsent 已存在时不插入")
    void initIfAbsent_present() {
        given(sysParamRepository.existsById("old")).willReturn(true);

        sysParamService.initIfAbsent("old", "v", "desc");

        verify(sysParamRepository, never()).save(any(SysParam.class));
    }
}
