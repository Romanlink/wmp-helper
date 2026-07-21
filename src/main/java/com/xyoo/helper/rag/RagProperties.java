package com.xyoo.helper.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 检索参数配置，命名空间 helper.rag。
 *
 * <pre>
 * helper.rag.top-k          召回片段数量（默认 5）
 * helper.rag.chunk-size     单切片最大字符数（默认 500）
 * helper.rag.chunk-overlap  相邻切片重叠字符数（默认 80）
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "helper.rag")
public class RagProperties {

    private int topK = 5;
    private int chunkSize = 500;
    private int chunkOverlap = 80;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
}
