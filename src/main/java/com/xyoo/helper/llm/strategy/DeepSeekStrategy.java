package com.xyoo.helper.llm.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyoo.helper.llm.AbstractOpenAiStrategy;
import com.xyoo.helper.llm.LlmProperties;
import org.springframework.stereotype.Service;

/**
 * DeepSeek 在线大模型策略（OpenAI 兼容协议）。
 * 协议细节全部复用 {@link AbstractOpenAiStrategy}，此处仅声明 provider 名称。
 */
@Service
public class DeepSeekStrategy extends AbstractOpenAiStrategy {

    public DeepSeekStrategy(LlmProperties props, ObjectMapper objectMapper) {
        super(props, objectMapper);
    }

    @Override
    public String provider() {
        return "deepseek";
    }
}
