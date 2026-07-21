package com.xyoo.helper.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding（文本向量化）配置，命名空间 helper.embedding。
 *
 * <pre>
 * helper.embedding.provider  实现：ollama（默认，本地）
 * helper.embedding.base-url  Ollama 服务地址
 * helper.embedding.model     模型名（默认 bge-m3，1024 维）
 * helper.embedding.dim       向量维度（需与 Qdrant collection 一致）
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "helper.embedding")
public class EmbeddingProperties {

    private String provider = "ollama";
    private String baseUrl = "http://localhost:11434";
    private String model = "bge-m3";
    private int dim = 1024;

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

    public int getDim() {
        return dim;
    }

    public void setDim(int dim) {
        this.dim = dim;
    }
}
