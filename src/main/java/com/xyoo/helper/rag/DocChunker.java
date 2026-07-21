package com.xyoo.helper.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文档切片器 —— 基于 Markdown 结构化切片。
 * <p>
 * 策略：按空行切分为段落（保留标题行作为段落起始），将段落累积到接近
 * {@code chunkSize} 时切出一块，并与上一块保留 {@code chunkOverlap} 字符的重叠，
 * 避免跨块语义断裂。适用于 PDF 解析出的 Markdown 正文。
 * </p>
 */
@Service
public class DocChunker {

    private final RagProperties ragProps;

    public DocChunker(RagProperties ragProps) {
        this.ragProps = ragProps;
    }

    /** 使用配置中的 chunkSize / chunkOverlap 切片 */
    public List<String> chunk(String markdown) {
        return chunk(markdown, ragProps.getChunkSize(), ragProps.getChunkOverlap());
    }

    /**
     * 切片主逻辑。
     *
     * @param markdown     Markdown 正文
     * @param chunkSize    单切片最大字符数
     * @param overlap      相邻切片重叠字符数
     */
    public List<String> chunk(String markdown, int chunkSize, int overlap) {
        if (markdown == null || markdown.isBlank()) {
            return Collections.emptyList();
        }
        List<String> paragraphs = splitParagraphs(markdown);
        if (paragraphs.isEmpty()) {
            return List.of(markdown.trim());
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : paragraphs) {
            // 当前块再加这个段落会超限 → 先切出当前块
            if (cur.length() > 0 && cur.length() + p.length() + 1 > chunkSize) {
                chunks.add(cur.toString().trim());
                // 保留末尾 overlap 字符作为下一块的开头（重叠）
                String tail = cur.toString();
                int start = Math.max(0, tail.length() - overlap);
                cur = new StringBuilder(tail.substring(start));
            }
            if (cur.length() > 0) {
                cur.append("\n");
            }
            cur.append(p);
        }
        if (cur.length() > 0) {
            chunks.add(cur.toString().trim());
        }

        return chunks.isEmpty() ? List.of(markdown.trim()) : chunks;
    }

    /** 按一个或多个空行将文本拆为段落（去除首尾空白） */
    private List<String> splitParagraphs(String md) {
        List<String> paras = new ArrayList<>();
        for (String block : md.split("\\n\\s*\\n")) {
            String t = block.trim();
            if (!t.isEmpty()) {
                paras.add(t);
            }
        }
        return paras;
    }
}
