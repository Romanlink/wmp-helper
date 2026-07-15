package com.xyoo.helper.llm.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyoo.helper.llm.AbstractOpenAiStrategy;
import com.xyoo.helper.llm.LlmProperties;
import org.springframework.stereotype.Service;

/**
 * 阿里云百炼 Qwen 在线大模型策略（OpenAI 兼容协议）。
 * 协议细节全部复用 {@link AbstractOpenAiStrategy}，此处仅声明 provider 名称。
 * 注意：配置 helper.llm.base-url 应指向兼容模式地址
 * （如 https://dashscope.aliyuncs.com/compatible-mode/v1），
 * 基类会自动拼接 /chat/completions。
 */
@Service
public class QwenStrategy extends AbstractOpenAiStrategy {

    public QwenStrategy(LlmProperties props, ObjectMapper objectMapper) {
        super(props, objectMapper);
    }

    @Override
    public String provider() {
        return "qwen";
    }
}
