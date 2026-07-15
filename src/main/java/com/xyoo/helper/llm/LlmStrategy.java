package com.xyoo.helper.llm;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * 大模型调用策略接口（策略模式）。
 *
 * 每种模型（Ollama / DeepSeek / Qwen …）实现本接口，彼此独立、互不干扰。
 * 由 {@link LlmStrategyFactory} 按 provider 名称分发，主流程无需关心具体协议。
 */
public interface LlmStrategy {

    /** provider 唯一标识，如 ollama / deepseek / qwen（小写） */
    String provider();

    /** 该策略当前是否可用（结合总开关与自身必要配置，如在线模型的 api-key） */
    default boolean isEnabled() {
        return true;
    }

    /** 不可用时返回给前端的友好提示 */
    default String disabledMessage() {
        return "当前模型 [" + provider() + "] 未启用或配置不完整。";
    }

    /**
     * 流式对话：将模型输出的增量 token 通过 {@code tokenConsumer} 逐块回传。
     *
     * @param message       用户提问
     * @param tokenConsumer token 消费者（由 Controller 写入 SSE 输出流）
     * @throws IOException 网络异常或客户端断开时抛出
     */
    void streamChat(String message, Consumer<String> tokenConsumer) throws IOException;
}
