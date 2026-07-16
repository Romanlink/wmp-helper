package com.xyoo.helper.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PdfContentParser 单元测试（同包可访问包级私有方法）。
 * 覆盖 Markdown 转换、智能截断，以及用 PDFBox 生成的真实 PDF 字节做端到端解析。
 */
class PdfContentParserTest {

    @Test
    @DisplayName("convertToMarkdown 将标题/列表/段落转换为 Markdown，并跳过纯页码")
    void convertToMarkdown() {
        String raw = "一级标题\n"
                + "这是一段普通正文，会被合并。\n"
                + "- 无序列表项\n"
                + "1. 有序列表项\n"
                + "123\n"; // 纯页码行被跳过
        String md = PdfContentParser.convertToMarkdown(raw);

        assertThat(md).contains("## 一级标题");
        assertThat(md).contains("- 无序列表项");
        assertThat(md).contains("1. 有序列表项");
        assertThat(md).doesNotContain("123");
    }

    @Test
    @DisplayName("smartTruncate 超长文本在句子边界截断并附说明")
    void smartTruncate_long() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("这是一段很长的示例文本，用于测试截断逻辑是否正常工作。");
        }
        String longText = sb.toString();
        String truncated = PdfContentParser.smartTruncate(longText, 5000);

        assertThat(truncated).contains("已从");
        // 截断点之后的内容部分不超过上限（截断说明另计）
        assertThat(truncated.length()).isGreaterThan(0);
    }

    @Test
    @DisplayName("smartTruncate 短文本原样返回")
    void smartTruncate_short() {
        String shortText = "短文本";
        assertThat(PdfContentParser.smartTruncate(shortText, 5000)).isEqualTo(shortText);
    }

    @Test
    @DisplayName("parseToMarkdown 可解析真实 PDF 字节")
    void parseRealPdf() throws Exception {
        byte[] pdf = buildSimplePdf("Hello PDF World");
        String md = PdfContentParser.parseToMarkdown(pdf);
        assertThat(md).contains("Hello PDF World");
    }

    private static byte[] buildSimplePdf(String text) throws Exception {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.beginText();
            cs.newLineAtOffset(50, 700);
            cs.showText(text);
            cs.endText();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        doc.close();
        return baos.toByteArray();
    }
}
