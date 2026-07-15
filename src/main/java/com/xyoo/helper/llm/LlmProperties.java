package com.xyoo.helper.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型接入配置（统一命名空间 helper.llm）。
 *
 * <pre>
 * helper.llm.enabled   总开关（默认 true）
 * helper.llm.provider  当前使用的模型：ollama | deepseek | qwen（默认 ollama）
 * helper.llm.base-url  接入地址（Ollama 本地地址 / DeepSeek / Qwen 在线地址）
 * helper.llm.model     模型名（如 qwen:7b / deepseek-chat / qwen-plus）
 * helper.llm.api-key   在线大模型 API Key（Ollama 本地可留空）
 * </pre>
 *
 * 为兼容历史部署脚本，默认值仍读取 OLLAMA_* 环境变量；在线模型切换时
 * 只需在 application.properties（或环境变量）中修改 provider / base-url / model / api-key。
 */
@Component
@ConfigurationProperties(prefix = "helper.llm")
public class LlmProperties {

    private boolean enabled = true;
    private String provider = "ollama";
    private String baseUrl = "http://localhost:11434";
    private String model = "qwen:7b";
    private String apiKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
