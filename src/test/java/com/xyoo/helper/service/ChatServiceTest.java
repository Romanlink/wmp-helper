package com.xyoo.helper.service;

import com.xyoo.helper.llm.LlmProperties;
import com.xyoo.helper.llm.LlmStrategy;
import com.xyoo.helper.llm.LlmStrategyFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ChatService 单元测试（纯 Mockito）。
 * 主流程只做「总开关校验 + 工厂分发」，因此重点验证四种分支：
 * 关闭、provider 未配置、策略自身禁用、正常分发。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private LlmProperties props;
    @Mock private LlmStrategyFactory factory;
    @Mock private LlmStrategy strategy;

    @InjectMocks private ChatService chatService;

    private Consumer<String> collector(List<String> sink) {
        return sink::add;
    }

    @Test
    @DisplayName("总开关关闭：提示未启用，且不调用工厂")
    void disabled() throws IOException {
        given(props.isEnabled()).willReturn(false);

        List<String> sink = new ArrayList<>();
        chatService.streamChat("hi", collector(sink));

        assertThat(sink).anyMatch(s -> s.contains("未启用"));
        verify(factory, never()).get(anyString());
    }

    @Test
    @DisplayName("provider 未配置：提示未配置可用模型")
    void providerNotFound() throws IOException {
        given(props.isEnabled()).willReturn(true);
        given(props.getProvider()).willReturn("unknown");
        given(factory.get("unknown")).willReturn(null);
        given(factory.providers()).willReturn(Set.of("ollama", "deepseek"));

        List<String> sink = new ArrayList<>();
        chatService.streamChat("hi", collector(sink));

        assertThat(sink).anyMatch(s -> s.contains("未配置可用的模型 provider"));
        verify(strategy, never()).streamChat(anyString(), any());
    }

    @Test
    @DisplayName("策略自身禁用：返回禁用提示且不分发")
    void strategyDisabled() throws IOException {
        given(props.isEnabled()).willReturn(true);
        given(props.getProvider()).willReturn("ollama");
        given(factory.get("ollama")).willReturn(strategy);
        given(strategy.isEnabled()).willReturn(false);
        given(strategy.disabledMessage()).willReturn("模型未启用");

        List<String> sink = new ArrayList<>();
        chatService.streamChat("hi", collector(sink));

        assertThat(sink).contains("模型未启用");
        verify(strategy, never()).streamChat(anyString(), any());
    }

    @Test
    @DisplayName("正常分发：调用策略 streamChat 并透传消息")
    void dispatch() throws IOException {
        given(props.isEnabled()).willReturn(true);
        given(props.getProvider()).willReturn("ollama");
        given(props.getModel()).willReturn("qwen:7b");
        given(factory.get("ollama")).willReturn(strategy);
        given(strategy.provider()).willReturn("ollama");
        given(strategy.isEnabled()).willReturn(true);

        List<String> sink = new ArrayList<>();
        chatService.streamChat("你好", collector(sink));

        verify(strategy).streamChat(eq("你好"), any());
    }
}
