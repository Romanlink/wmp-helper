package com.xyoo.helper.service;

import com.xyoo.helper.llm.LlmProperties;
import com.xyoo.helper.llm.LlmStrategy;
import com.xyoo.helper.llm.LlmStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * AI 对话服务（主流程）。
 *
 * <pre>
 * 职责只做两件事：
 *   1. 总开关校验（helper.llm.enabled）
 *   2. 按 helper.llm.provider 从工厂获取对应策略，分发流式调用
 * 具体模型协议（Ollama / DeepSeek / Qwen …）下沉到各自的 LlmStrategy 实现，
 * 主流程保持干净，新增模型无需改动此处。
 * </pre>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmProperties props;
    private final LlmStrategyFactory factory;

    public ChatService(LlmProperties props, LlmStrategyFactory factory) {
        this.props = props;
        this.factory = factory;
    }

    /**
     * 流式对话：根据配置选择策略实现，由其完成具体模型调用。
     *
     * @param message        用户提问
     * @param tokenConsumer  token 消费者（由 Controller 写入 SSE 输出流）
     * @throws IOException 网络异常或客户端断开时抛出
     */
    public void streamChat(String message, Consumer<String> tokenConsumer) throws IOException {
        // 1. 总开关
        if (!props.isEnabled()) {
            tokenConsumer.accept("当前未启用大模型。请在配置中开启 helper.llm.enabled=true。");
            return;
        }

        // 2. 解析策略
        LlmStrategy strategy = factory.get(props.getProvider());
        if (strategy == null) {
            tokenConsumer.accept("未配置可用的模型 provider：" + props.getProvider()
                    + "。可选值：" + factory.providers());
            return;
        }
        if (!strategy.isEnabled()) {
            tokenConsumer.accept(strategy.disabledMessage());
            return;
        }

        // 3. 分发（具体协议由策略实现，互不干扰）
        log.info("使用模型策略：{}（model={}）", strategy.provider(), props.getModel());
        strategy.streamChat(message, tokenConsumer);
    }
}
