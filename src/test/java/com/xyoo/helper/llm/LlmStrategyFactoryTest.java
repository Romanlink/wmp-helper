package com.xyoo.helper.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LlmStrategyFactory 单元测试：验证按 provider 分发（大小写不敏感）、
 * 未注册返回 null、providers() 返回已注册集合。
 */
class LlmStrategyFactoryTest {

    @Test
    @DisplayName("按 provider 分发（大小写不敏感），未注册返回 null")
    void dispatch() {
        LlmStrategy ollama = mock(LlmStrategy.class);
        when(ollama.provider()).thenReturn("ollama");
        LlmStrategy deepseek = mock(LlmStrategy.class);
        when(deepseek.provider()).thenReturn("deepseek");

        LlmStrategyFactory factory = new LlmStrategyFactory(Arrays.asList(ollama, deepseek));

        assertThat(factory.get("ollama")).isSameAs(ollama);
        assertThat(factory.get("Ollama")).isSameAs(ollama);
        assertThat(factory.get("DEEPSEEK")).isSameAs(deepseek);
        assertThat(factory.get("unknown")).isNull();
        assertThat(factory.get(null)).isNull();

        Set<String> providers = factory.providers();
        assertThat(providers).containsExactlyInAnyOrder("ollama", "deepseek");
    }
}
