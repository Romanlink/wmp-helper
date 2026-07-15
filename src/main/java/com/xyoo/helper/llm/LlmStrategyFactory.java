package com.xyoo.helper.llm;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大模型策略工厂（工厂模式 + 自动发现）。
 *
 * <p>Spring 会注入所有 {@link LlmStrategy} 实现，工厂按 provider 名称建索引。
 * 新增模型只需新增一个 LlmStrategy 实现类（标注 @Service），无需改动此处，
 * 天然满足「开闭原则」，各模型实现互不干扰。</p>
 */
@Component
public class LlmStrategyFactory {

    private final Map<String, LlmStrategy> strategies = new HashMap<>();

    public LlmStrategyFactory(List<LlmStrategy> strategyList) {
        for (LlmStrategy strategy : strategyList) {
            strategies.put(strategy.provider().toLowerCase(), strategy);
        }
    }

    /** 根据 provider 名称获取对应策略；找不到返回 null */
    public LlmStrategy get(String provider) {
        if (provider == null) {
            return null;
        }
        return strategies.get(provider.toLowerCase());
    }

    /** 当前已注册的所有 provider 名称（用于未知 provider 时的提示） */
    public Set<String> providers() {
        return strategies.keySet();
    }
}
