package com.xyoo.helper.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 内容解析工具：将 PDF 文件提取为 Markdown 格式文本。
 *
 * <pre>
 * 流程：
 *   1. 使用 PDFBox 提取原始文本
 *   2. 按行分析结构（标题 / 列表 / 段落）
 *   3. 组装为 Markdown
 *   4. 智能截断（在段落/句子边界处截断，保证语法完整）
 * </pre>
 */
public final class PdfContentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfContentParser.class);

    /** Markdown 最大字符数 */
    private static final int MAX_CHARS = 5000;

    /** 截断回退比例：从 maxChars 往前搜索，至少要覆盖 70% 的内容，否则用 sentence/word 级截断 */
    private static final double MIN_COVERAGE_RATIO = 0.70;

    private PdfContentParser() {
    }

    /**
     * 解析 PDF 文件内容并返回 Markdown 格式文本。
     *
     * @param pdfBytes PDF 文件的原始字节数组（解密后的明文）
     * @return Markdown 文本，超过 5000 字符自动在语法边界处截断
     * @throws IOException 解析失败时抛出
     */
    public static String parseToMarkdown(byte[] pdfBytes) throws IOException {
        String rawText;
        PDDocument document = null;
        try {
            document = PDDocument.load(pdfBytes);
            if (document.getNumberOfPages() == 0) {
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            rawText = stripper.getText(document);
        } catch (IOException e) {
            log.warn("PDF 解析失败: {}", e.getMessage());
            throw e;
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException ignored) {
                }
            }
        }

        if (rawText == null || rawText.trim().isEmpty()) {
            return "";
        }

        String markdown = convertToMarkdown(rawText);
        return smartTruncate(markdown, MAX_CHARS);
    }

    // ==================== Markdown 转换 ====================

    /**
     * 将原始 PDF 文本转换为 Markdown 格式。
     * <p>
     * 策略：
     * <ul>
     *   <li>短行 + 无标点结尾 → 二级标题（##）</li>
     *   <li>以 - / • / 1. 开头的行 → 无序/有序列表</li>
     *   <li>连续非空行 → 段落（合并后用空行分隔）</li>
     *   <li>多个连续空行 → 压缩为单个空行</li>
     * </ul>
     */
    static String convertToMarkdown(String rawText) {
        String[] rawLines = rawText.split("\\r?\\n");
        List<String> processed = new ArrayList<>();

        // 第一步：预处理——合并被换行打断的段落行、过滤页码和页眉页脚垃圾行
        List<String> merged = mergeBrokenParagraphs(rawLines);

        // 第二步：逐行分析输出 Markdown
        for (String line : merged) {
            String trimmed = line.trim();

            // 跳过空行，但要确保不输出连续的多个空行
            if (trimmed.isEmpty()) {
                if (!processed.isEmpty() && !processed.get(processed.size() - 1).isEmpty()) {
                    processed.add("");
                }
                continue;
            }

            // 跳过纯页码（短纯数字行）
            if (trimmed.matches("^\\d{1,4}$")) {
                continue;
            }

            // 检测二级标题：短行（≤ 60 字符）且不以标点结尾
            if (isHeading(trimmed)) {
                // 确保标题前后有空行
                if (!processed.isEmpty() && !processed.get(processed.size() - 1).isEmpty()) {
                    processed.add("");
                }
                processed.add("## " + trimmed);
                processed.add("");
                continue;
            }

            // 检测无序列表项
            if (trimmed.matches("^[\\-•·▪▸►›\\*]\\s+.+")) {
                String content = trimmed.replaceFirst("^[\\-•·▪▸►›\\*]\\s+", "");
                processed.add("- " + content);
                continue;
            }

            // 检测有序列表项
            if (trimmed.matches("^\\d+[.)]\\s+.+")) {
                String content = trimmed.replaceFirst("^\\d+[.)]\\s+", "");
                processed.add("1. " + content);
                continue;
            }

            // 普通段落行
            processed.add(trimmed);
        }

        // 第三步：后处理——合并连续段落行，确保段落间有空行隔开
        return postProcess(processed);
    }

    /**
     * 判断一行文本是否像标题。
     * 条件：长度 ≤ 60 字符，且不以常见句子结尾标点结束。
     */
    private static boolean isHeading(String line) {
        if (line.length() > 60) {
            return false;
        }
        // 不以句子结尾标点结束
        String lastChar = line.substring(line.length() - 1);
        return !lastChar.matches("[。！？，、；：,.!?;:]");
    }

    /**
     * 合并被 PDF 换行打断的段落行。
     * 规则：如果上一行不是空行且不以标点/列表标记结尾，且当前行不是列表/标题/空行，则合并。
     */
    private static List<String> mergeBrokenParagraphs(String[] lines) {
        List<String> result = new ArrayList<>();
        StringBuilder pending = new StringBuilder();

        for (String raw : lines) {
            String line = raw.trim();

            // 空行 → 提交当前段落后添加空行
            if (line.isEmpty()) {
                if (pending.length() > 0) {
                    result.add(pending.toString());
                    pending.setLength(0);
                }
                result.add("");
                continue;
            }

            // 标题/列表行 → 先提交 pending，再单独输出
            if (isHeading(line) || line.matches("^[\\-•·▪▸►›\\*]\\s+.+")
                    || line.matches("^\\d+[.)]\\s+.+")) {
                if (pending.length() > 0) {
                    result.add(pending.toString());
                    pending.setLength(0);
                }
                result.add(line);
                continue;
            }

            // 普通文本行 → 尝试与上一行合并
            if (pending.length() > 0) {
                // 上一行以标点结尾 → 可能是句子结束或换页导致的换行，先不合并
                String lastPendingChar = pending.substring(pending.length() - 1);
                if (lastPendingChar.matches("[。！？.!?]")) {
                    // 上一句已完整结束，提交后重新开始
                    result.add(pending.toString());
                    pending.setLength(0);
                    pending.append(line);
                } else {
                    // 合并：中间被 PDF 换行打断
                    pending.append(line);
                }
            } else {
                pending.append(line);
            }
        }

        // 提交最后一段
        if (pending.length() > 0) {
            result.add(pending.toString());
        }

        return result;
    }

    /**
     * 后处理：合并连续的非空段落行，确保段落间以空行分隔。
     */
    private static String postProcess(List<String> processed) {
        List<String> finalLines = new ArrayList<>();
        StringBuilder paragraphBuf = new StringBuilder();

        for (String line : processed) {
            if (line.isEmpty()) {
                // 空行：提交当前段落
                if (paragraphBuf.length() > 0) {
                    finalLines.add(paragraphBuf.toString());
                    paragraphBuf.setLength(0);
                }
                finalLines.add("");
                continue;
            }

            // Markdown 标题或列表项 → 先提交段落，再单独输出
            if (line.startsWith("## ") || line.startsWith("- ") || line.startsWith("1. ")) {
                if (paragraphBuf.length() > 0) {
                    finalLines.add(paragraphBuf.toString());
                    paragraphBuf.setLength(0);
                }
                finalLines.add(line);
                continue;
            }

            // 普通段落行 → 累积
            if (paragraphBuf.length() > 0) {
                paragraphBuf.append(" ");
            }
            paragraphBuf.append(line);
        }

        // 提交最后一段
        if (paragraphBuf.length() > 0) {
            finalLines.add(paragraphBuf.toString());
        }

        // 去除首尾空行
        while (!finalLines.isEmpty() && finalLines.get(0).isEmpty()) {
            finalLines.remove(0);
        }
        while (!finalLines.isEmpty() && finalLines.get(finalLines.size() - 1).isEmpty()) {
            finalLines.remove(finalLines.size() - 1);
        }

        return String.join("\n", finalLines);
    }

    // ==================== 智能截断 ====================

    /**
     * 在语法边界处截断文本，保证截断后 Markdown 语法完整。
     *
     * <p>优先级：
     * <ol>
     *   <li>段落边界（\n\n）——最自然</li>
     *   <li>句子边界（。！？\n）——次优</li>
     *   <li>单词边界（空格）——底线</li>
     * </ol>
     *
     * @param markdown Markdown 文本
     * @param maxChars 最大字符数
     * @return 截断后的文本（附加截断提示）
     */
    static String smartTruncate(String markdown, int maxChars) {
        if (markdown.length() <= maxChars) {
            return markdown;
        }

        int threshold = (int) (maxChars * MIN_COVERAGE_RATIO);

        // 1. 尝试在段落边界截断
        int cutPoint = markdown.lastIndexOf("\n\n", maxChars);
        if (cutPoint >= threshold) {
            return markdown.substring(0, cutPoint) + buildTruncationNote(markdown.length());
        }

        // 2. 尝试在句子边界截断（中文：。！？  英文：.!? 后跟空格或换行）
        cutPoint = findLastSentenceBreak(markdown, maxChars);
        if (cutPoint >= threshold) {
            return markdown.substring(0, cutPoint + 1) + buildTruncationNote(markdown.length());
        }

        // 3. 尝试在单词边界截断
        cutPoint = markdown.lastIndexOf(' ', maxChars);
        if (cutPoint >= threshold) {
            return markdown.substring(0, cutPoint) + buildTruncationNote(markdown.length());
        }

        // 4. 兜底：硬截断
        return markdown.substring(0, maxChars) + buildTruncationNote(markdown.length());
    }

    /**
     * 在 maxChars 范围内查找最后一个句子结束符的位置。
     */
    private static int findLastSentenceBreak(String text, int maxChars) {
        int searchEnd = Math.min(maxChars, text.length());
        int lastBreak = -1;

        for (int i = 0; i < searchEnd; i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                // 确保后面是空格、换行或结尾（避免截断在 Mr. / Dr. 这类缩写中间）
                if (i + 1 >= searchEnd || text.charAt(i + 1) == '\n'
                        || text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\r') {
                    lastBreak = i;
                }
            }
        }

        return lastBreak;
    }

    /**
     * 生成截断说明。
     */
    private static String buildTruncationNote(int totalChars) {
        return "\n\n> *（内容过长，已从 " + totalChars + " 字符截断至以上内容）*";
    }
}
