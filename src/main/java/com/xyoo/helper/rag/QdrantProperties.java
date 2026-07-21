package com.xyoo.helper.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qdrant 向量库配置，命名空间 helper.qdrant。
 *
 * <pre>
 * helper.qdrant.url         Qdrant REST 地址（默认 http://localhost:6333）
 * helper.qdrant.collection  集合名（默认 doc_chunks）
 * helper.qdrant.dim         向量维度（需与 embedding.dim 一致）
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "helper.qdrant")
public class QdrantProperties {

    private String url = "http://localhost:6333";
    private String collection = "doc_chunks";
    private int dim = 1024;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public int getDim() {
        return dim;
    }

    public void setDim(int dim) {
        this.dim = dim;
    }
}
